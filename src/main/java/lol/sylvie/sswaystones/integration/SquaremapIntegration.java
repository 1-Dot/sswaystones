/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.integration;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.*;
import lol.sylvie.sswaystones.Waystones;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.storage.WaystoneStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import xyz.jpenilla.squaremap.api.*;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/**
 * Integration with Squaremap to display public waystones on the web map. This
 * integration is optional and only activates if Squaremap is installed.
 */
public class SquaremapIntegration {
    private static final String LAYER_KEY = "sswaystones";
    private static final String ICON_SERVER = "sswaystones_server";
    private static final String ICON_PLAYER = "sswaystones_player";
    private static final int ICON_SIZE = 24;

    private static boolean initialized = false;
    private static MinecraftServer currentServer = null;
    private static final Map<WorldIdentifier, SimpleLayerProvider> layerProviders = new HashMap<>();

    /**
     * Check if Squaremap is available
     */
    public static boolean isSquaremapAvailable() {
        return FabricLoader.getInstance().isModLoaded("squaremap");
    }

    /**
     * Initialize the Squaremap integration when server starts
     */
    public static void initialize(MinecraftServer server) {
        if (!isSquaremapAvailable()) {
            return;
        }

        try {
            initializeInternal(server);
        } catch (Throwable e) {
            Waystones.LOGGER.warn("Failed to initialize Squaremap integration: {}", e.getMessage());
        }
    }

    private static void initializeInternal(MinecraftServer server) {
        currentServer = server;
        Squaremap api = SquaremapProvider.get();

        // Register custom icons
        registerIcons(api);

        // Register layer providers for each world
        for (MapWorld mapWorld : api.mapWorlds()) {
            WorldIdentifier worldId = mapWorld.identifier();

            SimpleLayerProvider provider = SimpleLayerProvider.builder("Waystones").showControls(true)
                    .defaultHidden(false).layerPriority(10).zIndex(100).build();

            mapWorld.layerRegistry().register(Key.of(LAYER_KEY), provider);
            layerProviders.put(worldId, provider);
        }

        initialized = true;
        Waystones.LOGGER.info("Squaremap integration initialized successfully!");

        // Initial update
        updateMarkers();
    }

    /**
     * Create and register waystone icons programmatically
     */
    private static void registerIcons(Squaremap api) {
        // Create server waystone icon (purple square diamond)
        BufferedImage serverIcon = createWaystoneIcon(new Color(164, 139, 252), new Color(0, 0, 0));
        api.iconRegistry().register(Key.of(ICON_SERVER), serverIcon);

        // Create player waystone icon (blue square diamond)
        BufferedImage playerIcon = createWaystoneIcon(new Color(134, 196, 249), new Color(0, 0, 0));
        api.iconRegistry().register(Key.of(ICON_PLAYER), playerIcon);
    }

    /**
     * Create a waystone marker icon - a square diamond (45° rotated square)
     */
    private static BufferedImage createWaystoneIcon(Color fillColor, Color borderColor) {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int centerX = ICON_SIZE / 2;
        int centerY = ICON_SIZE / 2;
        int halfSize = ICON_SIZE / 2 - 3; // Square diamond: equal width and height

        // Square diamond shape points (45° rotated square)
        int[] xPoints = {centerX, centerX + halfSize, centerX, centerX - halfSize};
        int[] yPoints = {centerY - halfSize, centerY, centerY + halfSize, centerY};

        // Draw filled diamond
        g2d.setColor(fillColor);
        g2d.fillPolygon(xPoints, yPoints, 4);

        // Draw border
        g2d.setColor(borderColor);
        g2d.setStroke(new java.awt.BasicStroke(2));
        g2d.drawPolygon(xPoints, yPoints, 4);

        // Add inner highlight
        g2d.setColor(new Color(255, 255, 255, 80));
        int innerSize = halfSize / 2;
        int[] innerX = {centerX, centerX + innerSize, centerX, centerX - innerSize};
        int[] innerY = {centerY - innerSize, centerY, centerY + innerSize, centerY};
        g2d.fillPolygon(innerX, innerY, 4);

        g2d.dispose();
        return image;
    }

    /**
     * Shutdown the integration when server stops
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }

        layerProviders.clear();
        currentServer = null;
        initialized = false;
    }

    /**
     * Update all waystone markers on the map. Call this when waystones are created,
     * removed, or their settings change.
     */
    public static void updateMarkers() {
        if (!initialized || currentServer == null) {
            return;
        }

        try {
            updateMarkersInternal();
        } catch (Throwable e) {
            Waystones.LOGGER.warn("Failed to update Squaremap markers: {}", e.getMessage());
        }
    }

    private static void updateMarkersInternal() {
        WaystoneStorage storage = WaystoneStorage.getServerState(currentServer);

        // Clear all existing markers
        for (SimpleLayerProvider provider : layerProviders.values()) {
            provider.clearMarkers();
        }

        // Add markers for all public waystones
        for (Map.Entry<String, WaystoneRecord> entry : storage.waystones.entrySet()) {
            WaystoneRecord waystone = entry.getValue();

            // Only show public (global) or server-owned waystones on the map
            if (!waystone.getAccessSettings().isGlobal() && !waystone.getAccessSettings().isServerOwned()) {
                continue;
            }

            // Convert ResourceKey to WorldIdentifier for matching
            String worldIdStr = waystone.getWorldKey().identifier().toString();
            WorldIdentifier worldId = WorldIdentifier.parse(worldIdStr);
            SimpleLayerProvider provider = layerProviders.get(worldId);

            if (provider == null) {
                continue;
            }

            BlockPos pos = waystone.getPos();
            String markerKey = entry.getKey();

            // Create marker point
            Point point = Point.of(pos.getX(), pos.getZ());

            // Build tooltip content
            String waystoneLabel = waystone.getWaystoneName();
            boolean isServerOwned = waystone.getAccessSettings().isServerOwned();
            String ownerInfo = isServerOwned ? "Server Waystone" : "Owner: " + waystone.getOwnerName();
            String coordsInfo = String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ());

            // Purple for server, blue for player
            String titleColor = isServerOwned ? "#a48bfc" : "#86c4f9";

            String tooltipHtml = String.format(
                    "<div style='text-align: center; padding: 4px;'>" + "<b style='color: %s;'>%s</b><br/>"
                            + "<span style='color: #AAA; font-size: 11px;'>%s</span><br/>"
                            + "<span style='color: #888; font-size: 10px;'>%s</span>" + "</div>",
                    titleColor, escapeHtml(waystoneLabel), escapeHtml(ownerInfo), coordsInfo);

            MarkerOptions options = MarkerOptions.builder().hoverTooltip(tooltipHtml).clickTooltip(tooltipHtml).build();

            // Use icon marker - icons maintain size across zoom levels
            Key iconKey = Key.of(isServerOwned ? ICON_SERVER : ICON_PLAYER);
            Marker marker = Marker.icon(point, iconKey, ICON_SIZE);
            marker.markerOptions(options);

            provider.addMarker(Key.of(markerKey), marker);
        }
    }

    /**
     * Helper method to escape HTML special characters
     */
    private static String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
                "&#39;");
    }

    /**
     * Called when a waystone is created or updated
     */
    public static void onWaystoneChanged(WaystoneRecord waystone) {
        updateMarkers();
    }

    /**
     * Called when a waystone is removed
     */
    public static void onWaystoneRemoved(WaystoneRecord waystone) {
        updateMarkers();
    }
}
