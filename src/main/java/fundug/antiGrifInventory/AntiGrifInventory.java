package fundug.antiGrifInventory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.scheduler.BukkitRunnable;

import javax.swing.text.html.HTMLDocument;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AntiGrifInventory extends JavaPlugin implements Listener {

    private Set<Material> blockedItems;
    private long requiredPlaytimeTicks;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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
            sendDelayedMessage(player, getConfig().getLong("delay-message", 60));
        }
        else{
            sendDelayedMessage(player, 0);
        }
    }

    private final Cache<UUID, Long> cooldownTicks = CacheBuilder.newBuilder()
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    private void sendDelayedMessage(Player player, long delayTicks) {
        UUID uuid = player.getUniqueId();

        Long lastTick = cooldownTicks.getIfPresent(uuid);
        long currentTick = Bukkit.getCurrentTick();

        if (lastTick != null && (currentTick - lastTick) < delayTicks) {
            return;
        }

        if (!player.isOnline()) return;

        String message = getConfig().getString("message", "<red>cВы не можете взять этот предмет!</red>");
        Component component = MINI_MESSAGE.deserialize(message);
        player.sendMessage(component);

        if (getConfig().getBoolean("show-remaining-time", true)) {
            long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            double remainingHours = (requiredPlaytimeTicks - playTimeTicks) / 20.0 / 60.0 / 60.0;

            String timeMessage = getConfig().getString("time-message", "<yellow>Осталось наиграть: % ч.<yellow>");
            String res = timeMessage.replace("%", String.format("%.1f", remainingHours));
            Component timeComponent = MINI_MESSAGE.deserialize(res);

            player.sendMessage(timeComponent);
        }

        cooldownTicks.put(uuid, currentTick);
    }
}