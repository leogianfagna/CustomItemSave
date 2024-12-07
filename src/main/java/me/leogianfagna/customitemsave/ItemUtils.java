package me.leogianfagna.customitemsave;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public class ItemUtils {

    public static String serializeItem(ItemStack item) {
        if (item == null) return null;

        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return config.saveToString();
    }

    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) return null;

        YamlConfiguration config = new YamlConfiguration();

        try {
            config.loadFromString(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return config.getItemStack("item");
    }
}