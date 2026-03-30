package net.ecocraft.api.currency;

public sealed interface Icon {
    record TextureIcon(String resourceLocation) implements Icon {}
    record ItemIcon(String itemId) implements Icon {}
    record FileIcon(String path) implements Icon {}
    record TextIcon(String symbol) implements Icon {}

    static Icon texture(String resourceLocation) { return new TextureIcon(resourceLocation); }
    static Icon item(String itemId) { return new ItemIcon(itemId); }
    static Icon file(String path) { return new FileIcon(path); }
    static Icon text(String symbol) { return new TextIcon(symbol); }

    static Icon parse(String descriptor) {
        if (descriptor.startsWith("texture:")) return texture(descriptor.substring(8));
        if (descriptor.startsWith("item:")) return item(descriptor.substring(5));
        if (descriptor.startsWith("file:")) return file(descriptor.substring(5));
        return text(descriptor);
    }
}
