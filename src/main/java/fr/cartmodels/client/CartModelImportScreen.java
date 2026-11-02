package fr.cartmodels.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.awt.image.BufferedImage;
import fr.cartmodels.model.CartModelInfo;
import fr.cartmodels.model.CartModelParser;
import fr.cartmodels.model.CartModelStore;
import fr.cartmodels.model.ModelSeat;
import fr.cartmodels.network.ModelNetwork;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CartModelImportScreen extends Screen {
    private EditBox nameInput;
    private Button importButton;
    private Button clearButton;
    private Button rotateButton;
    private Path selectedFile;
    private byte[] selectedBytes;
    private JsonObject previewModel;
    private ResourceLocation previewTexture;
    private Component status = Component.translatable("screen.cartmodels.import.drop_hint");
    private boolean statusSuccess;
    private boolean loading;
    private boolean uploading;
    private boolean replacementConfirmation;
    private final List<ModelSeat> selectedSeats = new ArrayList<>();
    private int previewRotation;
    private int cubeCount;
    private int textureCount;
    private int dataSize;
    private int selectionRevision;

    public CartModelImportScreen() {
        super(Component.translatable("screen.cartmodels.import.title"));
    }

    @Override
    protected void init() {
        int panelX = width / 2 - 145;
        int panelY = Math.max(48, height / 2 - 62);
        int infoX = panelX + 122;
        int infoWidth = Math.max(100, width - infoX - 12);

        nameInput = new EditBox(font, infoX, panelY, infoWidth, 20,
                Component.translatable("screen.cartmodels.import.name"));
        nameInput.setMaxLength(32);
        nameInput.setHint(Component.translatable("screen.cartmodels.import.name_hint"));
        nameInput.setResponder(ignored -> {
            replacementConfirmation = false;
            updateButtonState();
        });
        addRenderableWidget(nameInput);

        rotateButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.cartmodels.import.rotate"), button -> rotatePreview())
                .bounds(panelX, panelY + 116, 54, 20)
                .build());
        clearButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.cartmodels.import.clear"), button -> clearSelection())
                .bounds(panelX + 58, panelY + 116, 54, 20)
                .build());
        importButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.cartmodels.import.action"), button -> importFile())
                .bounds(width / 2 - 102, height - 28, 132, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 5, height - 28, 97, 20)
                .build());
        updateButtonState();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths.isEmpty()) {
            return;
        }
        if (paths.size() != 1) {
            setLocalStatus(Component.translatable("screen.cartmodels.import.one_file"), false);
            return;
        }
        Path file = paths.get(0);
        Path fileNamePath = file.getFileName();
        String fileName = fileNamePath == null ? "" : fileNamePath.toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".bbmodel") && !lowerName.endsWith(".json")) {
            setLocalStatus(Component.translatable("screen.cartmodels.import.unsupported"), false);
            return;
        }

        Path selectedPath = file.toAbsolutePath();
        selectedFile = selectedPath;
        int revision = ++selectionRevision;
        selectedBytes = null;
        previewModel = null;
        replacementConfirmation = false;
        releasePreview();
        suggestName(fileName);
        loading = true;
        setLocalStatus(Component.translatable("screen.cartmodels.import.reading"), true);
        updateButtonState();

        Minecraft client = minecraft;
        CompletableFuture.supplyAsync(() -> readAndParse(selectedPath, fileName, previewRotation))
                .whenComplete((preview, failure) -> client.execute(() -> {
                    if (client.screen != this || selectionRevision != revision) {
                        return;
                    }
                    loading = false;
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                ? failure.getCause() : failure;
                        setLocalStatus(Component.translatable("screen.cartmodels.import.invalid",
                                cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()),
                                false);
                        updateButtonState();
                        return;
                    }
                    try {
                        selectedBytes = preview.bytes();
                        previewModel = preview.model();
                        selectedSeats.clear();
                        cubeCount = preview.info().cubeCount();
                        textureCount = preview.info().embeddedTextureCount();
                        dataSize = preview.info().dataSizeBytes();
                        previewTexture = DynamicTextures.registerPreview(preview.image());
                        setLocalStatus(Component.translatable("screen.cartmodels.import.ready"), true);
                    } catch (IOException exception) {
                        selectedBytes = null;
                        previewModel = null;
                        setLocalStatus(Component.translatable("screen.cartmodels.import.preview_failed",
                                exception.getMessage()), false);
                    }
                    updateButtonState();
                }));
    }

    private static PreviewData readAndParse(Path file, String fileName, int rotation) {
        try {
            long fileSize = Files.size(file);
            if (fileSize == 0 || fileSize > CartModelParser.MAX_FILE_BYTES) {
                throw new IOException("Model files must be between 1 byte and 8 MiB.");
            }
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0 || bytes.length > CartModelParser.MAX_FILE_BYTES) {
                throw new IOException("Model files must be between 1 byte and 8 MiB.");
            }
            String contents = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            String normalized = CartModelParser.parse(fileName, contents);
            JsonObject model = JsonParser.parseString(normalized).getAsJsonObject();
            BufferedImage image = ModelPreviewRenderer.render(model, rotation, List.of(), true);
            return new PreviewData(bytes, model, CartModelInfo.inspect(normalized), image);
        } catch (IOException | IllegalStateException | JsonSyntaxException | IllegalArgumentException exception) {
            throw new CompletionException(exception);
        }
    }

    private void suggestName(String fileName) {
        if (!nameInput.getValue().isBlank()) {
            return;
        }
        int extension = fileName.lastIndexOf('.');
        String baseName = extension > 0 ? fileName.substring(0, extension) : fileName;
        String suggestion = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");
        suggestion = suggestion.replaceAll("^_+|_+$", "");
        if (suggestion.isEmpty()) {
            suggestion = "model";
        }
        nameInput.setValue(suggestion.substring(0, Math.min(32, suggestion.length())));
    }

    private void importFile() {
        if (loading || uploading || selectedBytes == null || selectedFile == null) {
            return;
        }
        String name = nameInput.getValue().trim();
        if (!CartModelStore.isValidName(name)) {
            setLocalStatus(Component.translatable("screen.cartmodels.import.invalid_name"), false);
            return;
        }
        if (ModelRenderCache.contains(name) && !replacementConfirmation) {
            replacementConfirmation = true;
            setLocalStatus(Component.translatable("screen.cartmodels.import.confirm_replace", name), false);
            updateButtonState();
            return;
        }
        try {
            JsonObject source = JsonParser.parseString(new String(selectedBytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray seats = new JsonArray();
            selectedSeats.forEach(seat -> seats.add(seat.toJson()));
            source.add("cartmodels_seats", seats);
            byte[] uploadBytes = source.toString().getBytes(StandardCharsets.UTF_8);
            if (uploadBytes.length > CartModelParser.MAX_FILE_BYTES) {
                setLocalStatus(Component.translatable("screen.cartmodels.import.seats_too_large"), false);
                return;
            }
            ModelNetwork.uploadModel(name, selectedFile.getFileName().toString(), uploadBytes);
            uploading = true;
            setLocalStatus(Component.translatable("screen.cartmodels.import.uploading"), true);
            updateButtonState();
        } catch (RuntimeException exception) {
            setLocalStatus(Component.translatable("screen.cartmodels.import.upload_failed",
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()),
                    false);
        }
    }

    private void rotatePreview() {
        if (previewModel == null) {
            return;
        }
        previewRotation++;
        try {
            ResourceLocation nextTexture = DynamicTextures.registerPreview(
                    ModelPreviewRenderer.render(previewModel, previewRotation, selectedSeats, true));
            releasePreview();
            previewTexture = nextTexture;
        } catch (IOException exception) {
            setLocalStatus(Component.translatable("screen.cartmodels.import.preview_failed",
                    exception.getMessage()), false);
        }
    }

    private void clearSelection() {
        selectionRevision++;
        selectedFile = null;
        selectedBytes = null;
        previewModel = null;
        selectedSeats.clear();
        cubeCount = 0;
        textureCount = 0;
        dataSize = 0;
        previewRotation = 0;
        replacementConfirmation = false;
        releasePreview();
        setLocalStatus(Component.translatable("screen.cartmodels.import.drop_hint"), true);
        updateButtonState();
    }

    private void releasePreview() {
        DynamicTextures.releasePreview(previewTexture);
        previewTexture = null;
    }

    private void updateButtonState() {
        if (importButton != null) {
            importButton.active = selectedBytes != null && !loading && !uploading;
            Component label = loading
                    ? Component.translatable("screen.cartmodels.import.preparing")
                    : uploading
                            ? Component.translatable("screen.cartmodels.import.uploading")
                            : replacementConfirmation
                                    ? Component.translatable("screen.cartmodels.import.confirm_action")
                                    : Component.translatable("screen.cartmodels.import.action");
            importButton.setMessage(label);
        }
        if (clearButton != null) {
            clearButton.active = selectedFile != null && !loading && !uploading;
        }
        if (rotateButton != null) {
            rotateButton.active = previewModel != null && !loading;
        }
    }

    public void setStatus(String message, boolean success) {
        uploading = false;
        setLocalStatus(Component.literal(message), success);
        updateButtonState();
    }

    private void setLocalStatus(Component message, boolean success) {
        status = message;
        statusSuccess = success;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && importButton != null && importButton.active) {
            importFile();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelX = width / 2 - 145;
        int panelY = Math.max(48, height / 2 - 62);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.cartmodels.import.drop_hint"),
                width / 2, 32, 0xD0D0D0);
        graphics.drawCenteredString(font, Component.translatable("screen.cartmodels.import.seats_hint"),
                width / 2, 43, 0xFFFFC857);
        graphics.fill(panelX - 1, panelY - 1, panelX + 113, panelY + 113, 0xFF59616B);
        graphics.fill(panelX, panelY, panelX + 112, panelY + 112, 0xFF20252B);
        if (previewTexture != null) {
            graphics.blit(previewTexture, panelX + 2, panelY + 2,
                    0.0F, 0.0F, 108, 108, 128, 128);
        } else {
            graphics.drawCenteredString(font, Component.translatable("screen.cartmodels.import.preview_empty"),
                    panelX + 56, panelY + 53, 0xFFB8C0CC);
        }
        int infoX = panelX + 122;
        graphics.drawString(font, Component.translatable("screen.cartmodels.import.file"), infoX, panelY + 28,
                0xFFB8C0CC);
        if (selectedFile != null && selectedFile.getFileName() != null) {
            String name = font.plainSubstrByWidth(selectedFile.getFileName().toString(),
                    Math.max(40, width - infoX - 12));
            graphics.drawString(font, name, infoX, panelY + 40, 0xFFFFFFFF);
        }
        if (previewModel != null) {
            graphics.drawString(font, Component.translatable("screen.cartmodels.import.cubes", cubeCount),
                    infoX, panelY + 65, 0xFFE2E8F0);
            graphics.drawString(font, Component.translatable("screen.cartmodels.import.textures", textureCount),
                    infoX, panelY + 78, 0xFFE2E8F0);
            graphics.drawString(font, Component.translatable("screen.cartmodels.import.bytes", dataSize),
                    infoX, panelY + 91, 0xFFE2E8F0);
            graphics.drawString(font, Component.translatable(
                    "screen.cartmodels.import.seats", selectedSeats.size(), ModelSeat.MAX_SEATS),
                    infoX, panelY + 104, 0xFFFFC857);
        }
        int statusColor = statusSuccess ? 0xFF80FF80 : 0xFFFF8080;
        graphics.drawCenteredString(font, font.plainSubstrByWidth(status.getString(), width - 18),
                width / 2, height - 43, statusColor);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        selectionRevision++;
        releasePreview();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && previewModel != null) {
            int panelX = width / 2 - 145;
            int panelY = Math.max(48, height / 2 - 62);
            double previewLeft = panelX + 2.0;
            double previewTop = panelY + 2.0;
            if (mouseX >= previewLeft && mouseX < previewLeft + 108
                    && mouseY >= previewTop && mouseY < previewTop + 108) {
                ModelSeat voxel = ModelPreviewRenderer.voxelAt(previewModel, previewRotation,
                        (mouseX - previewLeft) * 128.0 / 108.0,
                        (mouseY - previewTop) * 128.0 / 108.0);
                if (voxel != null) {
                    if (!selectedSeats.remove(voxel)) {
                        if (selectedSeats.size() == ModelSeat.MAX_SEATS) {
                            setLocalStatus(Component.translatable("screen.cartmodels.import.seat_limit"), false);
                        } else {
                            selectedSeats.add(voxel);
                        }
                    }
                    replacementConfirmation = false;
                    refreshPreview();
                    updateButtonState();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void refreshPreview() {
        if (previewModel == null) {
            return;
        }
        try {
            ResourceLocation nextTexture = DynamicTextures.registerPreview(
                    ModelPreviewRenderer.render(previewModel, previewRotation, selectedSeats, true));
            releasePreview();
            previewTexture = nextTexture;
        } catch (IOException exception) {
            setLocalStatus(Component.translatable("screen.cartmodels.import.preview_failed",
                    exception.getMessage()), false);
        }
    }

    private record PreviewData(byte[] bytes, JsonObject model, CartModelInfo info, BufferedImage image) {
    }
}
