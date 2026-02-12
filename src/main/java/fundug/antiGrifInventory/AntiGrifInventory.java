package fundug.antiGrifInventory;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AntiGrifInventory extends JavaPlugin implements Listener {

    private Set<Material> blockedItems;
    private long requiredPlaytimeTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        loadBlockedItems();

        int requiredHours = getConfig().getInt("required-playtime-hours", 48);
        requiredPlaytimeTicks = requiredHours * 60 * 60 * 20L; // Конвертируем часы в тики (20 тиков = 1 секунда)

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("AntiGrifInventory включен! Заблокировано предметов: " + blockedItems.size());
        getLogger().info("Требуемое время игры: " + requiredHours + " часов");
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiGrifInventory выключен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("antigrifinventory")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("antigrifinventory.reload")) {
                    sender.sendMessage("§cУ вас нет прав для использования этой команды!");
                    return true;
                }

                reloadConfig();
                loadBlockedItems();

                int requiredHours = getConfig().getInt("required-playtime-hours", 48);
                requiredPlaytimeTicks = requiredHours * 60 * 60 * 20L;

                sender.sendMessage("§aКонфигурация перезагружена!");
                sender.sendMessage("§aЗаблокировано предметов: " + blockedItems.size());
                sender.sendMessage("§aТребуемое время игры: " + requiredHours + " часов");
                return true;
            } else if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cЭта команда только для игроков!");
                    return true;
                }

                Player player = (Player) sender;
                long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                double playTimeHours = playTimeTicks / 20.0 / 60.0 / 60.0;
                double requiredHours = requiredPlaytimeTicks / 20.0 / 60.0 / 60.0;

                player.sendMessage("§6Ваше время игры: §e" + String.format("%.2f", playTimeHours) + " часов");
                player.sendMessage("§6Требуется: §e" + String.format("%.2f", requiredHours) + " часов");

                if (playTimeTicks >= requiredPlaytimeTicks) {
                    player.sendMessage("§aВы можете брать все предметы!");
                } else {
                    double remaining = requiredHours - playTimeHours;
                    player.sendMessage("§cОсталось наиграть: §e" + String.format("%.2f", remaining) + " часов");
                }
                return true;
            }
        }
        return false;
    }

    private void loadBlockedItems() {
        blockedItems = new HashSet<>();
        FileConfiguration config = getConfig();
        List<String> items = config.getStringList("blocked-items");

        for (String itemName : items) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                blockedItems.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Неизвестный предмет в конфиге: " + itemName);
            }
        }
    }

    private boolean hasEnoughPlaytime(Player player) {
        if (player.hasPermission("antigrifinventory.bypass")) {
            return true;
        }

        long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return playTimeTicks >= requiredPlaytimeTicks;
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (hasEnoughPlaytime(player)) {
            return;
        }

        Material itemType = event.getItem().getItemStack().getType();

        if (blockedItems.contains(itemType)) {
            event.setCancelled(true);

            MessageCanUsefale(player, true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (hasEnoughPlaytime(player)) {
            return;
        }

        if (event.getCurrentItem() != null) {
            Material itemType = event.getCurrentItem().getType();

            if (blockedItems.contains(itemType) && event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);

                MessageCanUsefale(player, false);
            }
        }
    }

    private void MessageCanUsefale(Player player, boolean useDelay) {
        if(useDelay){
            sendDelayedMessage(player, 60);
        }
        else{
            sendDelayedMessage(player, 0);
        }
    }

    private void sendDelayedMessage(Player player, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                String message = getConfig().getString("message", "&cВы не можете взять этот предмет!");
                player.sendMessage(message.replace("&", "§"));

                if (getConfig().getBoolean("show-remaining-time", true)) {
                    long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    double remainingHours = (requiredPlaytimeTicks - playTimeTicks) / 20.0 / 60.0 / 60.0;
                    player.sendMessage("§eОсталось наиграть: " + String.format("%.1f", remainingHours) + " ч.");

                }
            }
        }, delayTicks);
    }
}