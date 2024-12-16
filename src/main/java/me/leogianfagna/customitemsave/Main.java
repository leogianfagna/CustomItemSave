package me.leogianfagna.customitemsave;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Main extends JavaPlugin {

    private Connection connection;

    @Override
    public void onEnable() {
        getLogger().info("CustomItem Plugin iniciado.");
        initDatabase();
    }

    @Override
    public void onDisable() {
        closeDatabase();
        getLogger().info("CustomItem Plugin desativado.");
    }

    private void initDatabase() {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                return;
            }

            File databaseFile = new File(dataFolder, "customitems.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS items (name TEXT PRIMARY KEY, nbt TEXT)")) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            getLogger().severe("Erro ao conectar ao banco de dados: " + e.getMessage());
        }
    }

    private void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().severe("Erro ao fechar o banco de dados: " + e.getMessage());
        }
    }

    private boolean saveItem(String name, ItemStack item) {
        try {
            String serializedItem = ItemUtils.serializeItem(item);

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO items (name, nbt) VALUES (?, ?)")) {
                statement.setString(1, name);
                statement.setString(2, serializedItem);
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            getLogger().severe("Erro ao salvar item: " + e.getMessage());
            return false;
        }
    }

    private ItemStack getItem(String name) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT nbt FROM items WHERE name = ?")) {
            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String serializedItem = resultSet.getString("nbt");
                    return ItemUtils.deserializeItem(serializedItem);
                }
            }
        } catch (SQLException e) {
            getLogger().severe("Erro ao recuperar item: " + e.getMessage());
        }
        return null;
    }

    private List<String> getAllItems() {
        List<String> itemNames = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM items ORDER BY name")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    itemNames.add(resultSet.getString("name"));
                }
            }
        } catch (SQLException e) {
            getLogger().severe("Erro ao recuperar itens do banco de dados: " + e.getMessage());
        }
        return itemNames;
    }

    private boolean removeItem(String name) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM items WHERE name = ?")) {
            statement.setString(1, name);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            getLogger().severe("Erro ao remover item: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player;
        if (command.getName().equalsIgnoreCase("customitem")) {
            if (args.length < 1) {
                sender.sendMessage("§cUso: /customitem <save|get|give|remove> [nome] [jogador]");
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "save":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Este comando só pode ser executado por jogadores.");
                        return true;
                    }

                    player = (Player) sender;
                    if (args.length < 2) {
                        player.sendMessage("§cUso: /customitem save <nome>");
                        return true;
                    }
                    ItemStack itemInHand = player.getInventory().getItemInMainHand();
                    if (itemInHand == null || itemInHand.getType().isAir()) {
                        player.sendMessage("§cVocê precisa estar segurando um item.");
                        return true;
                    }
                    if (saveItem(args[1], itemInHand)) {
                        player.sendMessage("§aItem salvo com sucesso como '" + args[1] + "'.");
                    } else {
                        player.sendMessage("§cErro ao salvar o item.");
                    }
                    break;

                case "get":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Este comando só pode ser executado por jogadores.");
                        return true;
                    }

                    player = (Player) sender;
                    if (args.length < 2) {
                        player.sendMessage("§cUso: /customitem get <nome>");
                        return true;
                    }
                    ItemStack retrievedItem = getItem(args[1]);
                    if (retrievedItem != null) {
                        player.getInventory().addItem(retrievedItem);
                        player.sendMessage("§aItem '" + args[1] + "' resgatado com sucesso.");
                    } else {
                        player.sendMessage("§cItem não encontrado.");
                    }
                    break;

                case "give":
                    if (args.length < 3) {
                        sender.sendMessage("§cUso: /customitem give <jogador> <nome>");
                        return true;
                    }
                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                    if (targetPlayer == null) {
                        sender.sendMessage("§cJogador não encontrado.");
                        return true;
                    }
                    ItemStack itemToGive = getItem(args[2]);
                    if (itemToGive != null) {
                        targetPlayer.getInventory().addItem(itemToGive);
                        sender.sendMessage("§aItem dado ao jogador " + targetPlayer.getName() + ".");
                    } else {
                        sender.sendMessage("§cItem não encontrado.");
                    }
                    break;

                case "remove":
                    if (args.length < 2) {
                        sender.sendMessage("§cUso: /customitem remove <nome>");
                        return true;
                    }
                    if (removeItem(args[1])) {
                        sender.sendMessage("§aItem '" + args[1] + "' removido com sucesso.");
                    } else {
                        sender.sendMessage("§cErro ao remover o item ou item não encontrado.");
                    }
                    break;

                case "list":
                    List<String> allItems = getAllItems();
                    if (allItems.isEmpty()) {
                        sender.sendMessage("§7Nenhum item foi salvo ainda.");
                    } else {
                        sender.sendMessage("§aItens salvos:");
                        for (String itemName : allItems) {
                            sender.sendMessage("§e- §7" + itemName);
                        }
                    }
                    break;

                default:
                    sender.sendMessage("§cComando inválido. Uso: /customitem <save|get|give|remove> [nome] [jogador]");
                    break;
            }
        }

        return true;
    }
}
