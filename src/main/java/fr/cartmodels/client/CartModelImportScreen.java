package fr.cartmodels.client;

import fr.cartmodels.model.CartModelStore;
import fr.cartmodels.network.ModelNetwork;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CartModelImportScreen extends Screen {
    private EditBox nameInput;
    private String selectedFile = "";
    private String status = "Drop a .bbmodel or Minecraft model .json file here.";
    private boolean statusSuccess;

    public CartModelImportScreen() {
        super(Component.literal("Import Blockbench Model"));
    }

    @Override
    protected void init() {
        nameInput = new EditBox(font, width / 2 - 100, height / 2 - 42, 200, 20, Component.literal("Model name"));
        nameInput.setMaxLength(32);
        nameInput.setHint(Component.literal("model_name"));
        addRenderableWidget(nameInput);
        addRenderableWidget(Button.builder(Component.literal("Import"), button -> importFile())
                .bounds(width / 2 - 100, height / 2 + 12, 200, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 38, 200, 20)
                .build());
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths.isEmpty()) {
            return;
        }
        Path file = paths.get(0);
        String fileName = file.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".bbmodel") && !lowerName.endsWith(".json")) {
            setStatus("Choose a .bbmodel or .json model file.", false);
            return;
        }
        selectedFile = file.toAbsolutePath().toString();
        if (nameInput.getValue().isBlank()) {
            String suggestion = fileName.substring(0, fileName.lastIndexOf('.')).replaceAll("[^a-zA-Z0-9_-]", "_");
            nameInput.setValue(suggestion.substring(0, Math.min(32, suggestion.length())));
        }
        setStatus("Selected: " + fileName, true);
    }

    private void importFile() {
        if (selectedFile.isEmpty()) {
            setStatus("Drop a model file into this screen first.", false);
            return;
        }
        String name = nameInput.getValue().trim();
        if (!CartModelStore.isValidName(name)) {
            setStatus("Names must be 1–32 letters, numbers, underscores, or hyphens.", false);
            return;
        }
        try {
            Path file = Path.of(selectedFile);
            long fileSize = Files.size(file);
            if (fileSize == 0 || fileSize > fr.cartmodels.model.CartModelParser.MAX_FILE_BYTES) {
                setStatus("Model files must be between 1 byte and 8 MiB.", false);
                return;
            }
            ModelNetwork.uploadModel(name, file.getFileName().toString(), Files.readAllBytes(file));
            setStatus("Uploading model…", true);
        } catch (IOException | SecurityException exception) {
            setStatus("Could not read the selected file: " + exception.getMessage(), false);
        }
    }

    public void setStatus(String message, boolean success) {
        status = message;
        statusSuccess = success;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 82, 0xFFFFFF);
        graphics.drawCenteredString(font, "Model name", width / 2, height / 2 - 54, 0xA0A0A0);
        graphics.drawCenteredString(font, "Drag and drop one model file into this window.",
                width / 2, height / 2 - 12, 0xD0D0D0);
        int statusColor = statusSuccess ? 0x80FF80 : 0xFF8080;
        graphics.drawCenteredString(font, font.plainSubstrByWidth(status, width - 24), width / 2,
                height / 2 + 70, statusColor);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }
}
