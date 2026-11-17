package com.planetarioid.proximityplushrdanick;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class ProximityplusHRDAnick extends JavaPlugin implements Listener {

    private static final String HRDA_NICK_API = "cz.hrda.hrdaAPI.api.HrdaNickAPI";

    private double radiusSquared;
    private Method getNickMethod;
    private boolean nicknameApiAvailable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadRadius();
        hookHrdaNickApi();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Enabled. Announcement radius: " + Math.sqrt(radiusSquared) + " blocks.");
    }

    private void reloadRadius() {
        int chunks = Math.max(1, getConfig().getInt("radius-chunks", 12));
        double blocks = chunks * 16.0;
        radiusSquared = blocks * blocks;
    }

    private void hookHrdaNickApi() {
        if (getServer().getPluginManager().getPlugin("HrdaAPI") == null) {
            getLogger().warning("HrdaAPI was not found. Player usernames will be used until HrdaAPI is available.");
            return;
        }

        try {
            Class<?> apiClass = Class.forName(HRDA_NICK_API, true, getClass().getClassLoader());
            getNickMethod = apiClass.getMethod("getNick", Player.class);
            nicknameApiAvailable = true;
            getLogger().info("Connected to HrdaAPI nickname support.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            nicknameApiAvailable = false;
            getLogger().log(Level.WARNING,
                    "Could not hook HrdaAPI. Falling back to normal usernames.", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        String message = event.getJoinMessage();
        event.setJoinMessage(null);
        if (message != null && !message.isEmpty()) {
            sendNearby(event.getPlayer(), event.getPlayer().getLocation(), replaceOnlineNames(message), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String message = event.getQuitMessage();
        event.setQuitMessage(null);
        if (message != null && !message.isEmpty()) {
            sendNearby(event.getPlayer(), event.getPlayer().getLocation(), replaceOnlineNames(message), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        String message = event.getDeathMessage();
        event.setDeathMessage(null);
        if (message != null && !message.isEmpty()) {
            sendNearby(event.getEntity(), event.getEntity().getLocation(), replaceOnlineNames(message), true);
        }
    }

    private void sendNearby(Player eventPlayer, Location eventLocation, String message, boolean includeEventPlayer) {
        World eventWorld = eventLocation.getWorld();
        if (eventWorld == null) {
            return;
        }

        for (Player recipient : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (!includeEventPlayer && recipient.getUniqueId().equals(eventPlayer.getUniqueId())) {
                continue;
            }
            if (!recipient.getWorld().getUID().equals(eventWorld.getUID())) {
                continue;
            }

            if (recipient.getLocation().distanceSquared(eventLocation) <= radiusSquared) {
                recipient.sendMessage(message);
            }
        }
    }

    private String replaceOnlineNames(String message) {
        String result = message;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String username = player.getName();
            String nickname = getNickname(player);
            if (nickname.equals(username)) {
                continue;
            }

            Pattern namePattern = Pattern.compile(
                    "(?<![A-Za-z0-9_])" + Pattern.quote(username) + "(?![A-Za-z0-9_])"
            );
            result = namePattern.matcher(result).replaceAll(java.util.regex.Matcher.quoteReplacement(nickname));
        }
        return result;
    }

    private String getNickname(Player player) {
        if (!nicknameApiAvailable || getNickMethod == null) {
            return player.getName();
        }

        try {
            Object value = getNickMethod.invoke(null, player);
            if (value instanceof String nickname && !nickname.isBlank()) {
                return nickname;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().log(Level.FINE, "Could not read nickname for " + player.getName(), exception);
        }
        return player.getName();
    }
}
