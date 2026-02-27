/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.*;
import java.util.ArrayList;
import java.util.List;
import lol.sylvie.sswaystones.integration.SquaremapIntegration;
import lol.sylvie.sswaystones.storage.PlayerData;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.storage.WaystoneStorage;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

public class JavaViewerGui extends SimpleGui {
    private static final int ITEMS_PER_PAGE = 9 * 5;
    private final WaystoneRecord waystone;
    private int pageIndex = 0;

    private final WaystoneStorage storage;
    private final PlayerData playerData;
    private final List<WaystoneRecord> favorites;
    private final List<WaystoneRecord> nonFavorites;
    private final boolean hasFavorites;
    private final int maxPages;

    public JavaViewerGui(ServerPlayer player, @Nullable WaystoneRecord waystone) {
        super(MenuType.GENERIC_9x6, player, false);
        this.waystone = waystone;

        this.storage = WaystoneStorage.getServerState(player.level().getServer());
        this.playerData = WaystoneStorage.getPlayerState(player);

        // Get accessible waystones (includes current waystone in normal sort order)
        List<WaystoneRecord> accessible = storage.getAccessibleWaystones(player, null);

        // Separate favorites and non-favorites
        this.favorites = new ArrayList<>();
        this.nonFavorites = new ArrayList<>();

        for (WaystoneRecord record : accessible) {
            if (playerData.isFavorite(record.getHash())) {
                this.favorites.add(record);
            } else {
                this.nonFavorites.add(record);
            }
        }

        this.hasFavorites = !favorites.isEmpty();

        // Calculate max pages: if has favorites, page 0 is favorites, then regular
        // pages
        int totalItems = hasFavorites ? nonFavorites.size() : getAllAccessible().size();
        int regularPages = Math.max(Math.ceilDiv(totalItems, ITEMS_PER_PAGE), 1);
        this.maxPages = hasFavorites ? regularPages + 1 : regularPages;

        this.updateMenu();
    }

    public void updateMenu() {
        // Determine if this is the favorites page
        boolean isFavoritesPage = hasFavorites && pageIndex == 0;

        // Update title
        if (waystone != null) {
            String pageSuffix = isFavoritesPage ? "★" : String.valueOf(pageIndex + 1);
            this.setTitle(Component.literal(String.format("%s [%s] (%s/%s)", waystone.getWaystoneName(),
                    waystone.getOwnerName(), pageSuffix, maxPages)));
        } else {
            String pageSuffix = isFavoritesPage ? "★" : String.valueOf(pageIndex + 1);
            this.setTitle(Component.literal(String.format("Waystones (%s/%s)", pageSuffix, maxPages)));
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            this.clearSlot(i);
        }

        // Determine which list to display and calculate offset
        List<WaystoneRecord> displayList;
        int offset;

        if (isFavoritesPage) {
            displayList = this.favorites;
            offset = 0;
        } else {
            displayList = hasFavorites ? this.nonFavorites : getAllAccessible();
            // If has favorites, page 1 is the first regular page (index 0 in nonFavorites)
            int regularPageIndex = hasFavorites ? pageIndex - 1 : pageIndex;
            offset = ITEMS_PER_PAGE * regularPageIndex;
        }

        for (int i = offset; i < displayList.size(); i++) {
            WaystoneRecord record = displayList.get(i);
            int slot = i - offset;
            if (slot >= 45)
                break;

            boolean isFavorite = playerData.isFavorite(record.getHash());
            GuiElementBuilder element = createWaystoneElement(record, isFavorite);
            this.setSlot(slot, element);
        }

        for (int i = 45; i < 54; i++) {
            this.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).setName(Component.empty()));
        }

        // Gui controls
        this.setSlot(45,
                new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(IconConstants.ARROW_LEFT)
                        .setName(Component.translatable("gui.sswaystones.page_previous"))
                        .setCallback((index, type, action, gui) -> previousPage()));

        this.setSlot(47,
                new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(IconConstants.ARROW_RIGHT)
                        .setName(Component.translatable("gui.sswaystones.page_next"))
                        .setCallback((index, type, action, gui) -> nextPage()));

        // Waystone settings
        if (waystone == null)
            return;

        if (waystone.canPlayerEdit(player)) {
            if (Permissions.check(player, "sswaystones.manager", 4)
                    && !waystone.getOwnerUUID().equals(player.getUUID())) {
                this.setSlot(50, new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(IconConstants.CHEST)
                        .setName(Component.translatable("gui.sswaystones.steal_waystone").withStyle(ChatFormatting.RED))
                        .setCallback((index, type, action, gui) -> {
                            waystone.setOwner(player);
                            this.updateMenu();
                        }));
            }

            // Setting menus
            this.setSlot(51, new GuiElementBuilder(waystone.getIconOrHead(player.level().getServer()))
                    .setName(Component.translatable("gui.sswaystones.change_icon").withStyle(ChatFormatting.YELLOW))
                    .glow().setCallback((index, type, action, gui) -> new IconGui(waystone, player).open()));

            this.setSlot(52, new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(IconConstants.ANVIL)
                    .setName(Component.translatable("gui.sswaystones.change_name").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, type, action, gui) -> new NameGui(waystone, player).open()));

            this.setSlot(53,
                    new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(IconConstants.COMMAND_BLOCK)
                            .setName(Component.translatable("gui.sswaystones.access_settings")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE))
                            .setCallback((index, type, action, gui) -> new AccessSettingsGui(waystone, player).open()));
        }
    }

    public void previousPage() {
        pageIndex--;
        if (pageIndex < 0) {
            pageIndex = maxPages - 1;
        }

        this.updateMenu();
    }

    public void nextPage() {
        pageIndex++;
        if (pageIndex >= maxPages) {
            pageIndex = 0;
        }

        this.updateMenu();
    }

    private List<WaystoneRecord> getAllAccessible() {
        List<WaystoneRecord> all = new ArrayList<>();
        all.addAll(favorites);
        all.addAll(nonFavorites);
        return all;
    }

    private GuiElementBuilder createWaystoneElement(WaystoneRecord record, boolean isFavorite) {
        boolean isCurrentWaystone = waystone != null && record.getHash().equals(waystone.getHash());

        GuiElementBuilder element = new GuiElementBuilder(record.getIconOrHead(player.level().getServer()))
                .setName(record.getWaystoneText().copy().withStyle(ChatFormatting.YELLOW));

        // Dimension display logic
        int dimensionCount = 1;
        ChatFormatting dimensionColor = ChatFormatting.AQUA; // Default for overworld
        String dimensionId = record.getWorldKey().identifier().toString();

        if ("minecraft:the_nether".equals(dimensionId)) {
            dimensionCount = 2;
            dimensionColor = ChatFormatting.RED; // Nether
        } else if ("minecraft:the_end".equals(dimensionId)) {
            dimensionCount = 3;
            dimensionColor = ChatFormatting.BLUE; // End
        }

        // Current waystone uses count 64 to distinguish
        if (isCurrentWaystone) {
            dimensionCount = 64;
        }

        element.setCount(dimensionCount);
        element.setName(record.getWaystoneText().copy().withStyle(dimensionColor));

        if (record.getAccessSettings().isServerOwned()) {
            element.glow(true);
        }

        List<Component> loreLines = new ArrayList<>();

        // Show "Current Location" indicator for current waystone
        if (isCurrentWaystone) {
            loreLines.add(
                    Component.translatable("gui.sswaystones.current_location").withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        BlockPos pos = record.getPos();
        String coords = String.format("%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
        loreLines.add(Component.nullToEmpty(coords));

        if (!record.getAccessSettings().isServerOwned()) {
            loreLines.add(Component.nullToEmpty(record.getOwnerName()).copy().withStyle(ChatFormatting.GRAY));
        }

        // Add favorite indicator
        if (isFavorite) {
            loreLines.add(Component.literal("★ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("gui.sswaystones.favorite").withStyle(ChatFormatting.GOLD)));
        }

        // Add XP cost display (not for current waystone since you can't teleport to
        // yourself)
        if (!isCurrentWaystone) {
            int xpCost = record.getXpCost(player);
            if (xpCost > 0) {
                loreLines
                        .add(Component.translatable("gui.sswaystones.xp_cost", xpCost).withStyle(ChatFormatting.GREEN));
            }
        }

        loreLines.add(Component.empty());
        if (isCurrentWaystone) {
            loreLines.add(Component.translatable("gui.sswaystones.shift_click_to_toggle_favorite")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            loreLines.add(
                    Component.translatable("gui.sswaystones.click_to_teleport").withStyle(ChatFormatting.DARK_GRAY));
            loreLines.add(Component.translatable("gui.sswaystones.shift_click_to_toggle_favorite")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        element.setLore(loreLines);

        element.setCallback((index, type, action, gui) -> {
            if (type == ClickType.MOUSE_LEFT_SHIFT || type == ClickType.MOUSE_RIGHT_SHIFT) {
                // Toggle favorite
                String hash = record.getHash();
                boolean wasFavorite = playerData.isFavorite(hash);

                if (wasFavorite) {
                    // Remove from favorites
                    playerData.toggleFavorite(hash);
                    favorites.remove(record);
                    nonFavorites.addFirst(record);
                } else if (playerData.canAddFavorite()) {
                    // Add to favorites
                    playerData.toggleFavorite(hash);
                    nonFavorites.remove(record);
                    favorites.add(record);
                } else {
                    // Can't add, favorites full
                    player.sendSystemMessage(
                            Component.translatable("error.sswaystones.favorites_full").withStyle(ChatFormatting.RED));
                }

                // Rebuild GUI to update favorites page visibility
                ViewerUtil.openJavaGui(player, waystone);
            } else {
                // Don't teleport if clicking on current waystone
                boolean clickedCurrentWaystone = waystone != null && record.getHash().equals(waystone.getHash());
                if (!clickedCurrentWaystone) {
                    record.handleTeleport(player);
                    gui.close();
                }
            }
        });

        return element;
    }

    protected static class NameGui extends AnvilInputGui {
        private final WaystoneRecord waystone;

        public NameGui(WaystoneRecord waystone, ServerPlayer player) {
            super(player, false);
            this.waystone = waystone;

            this.setDefaultInputValue(waystone.getWaystoneName());
            this.setSlot(1,
                    new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(IconConstants.CANCEL)
                            .setName(Component.translatable("gui.back"))
                            .setCallback((index, type, action, gui) -> gui.close()));

            this.setSlot(2, new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(IconConstants.CHECKMARK)
                    .setName(Component.translatable("gui.done")).setCallback((index, type, action, gui) -> {
                        String input = this.getInput();
                        waystone.setWaystoneName(input);
                        gui.close();
                    }));

            this.setTitle(Component.translatable("gui.sswaystones.change_name_title"));
        }

        @Override
        public void onClose() {
            super.onClose();
            ViewerUtil.openJavaGui(player, waystone);
        }
    }

    protected static class IconGui extends SimpleGui {
        private final WaystoneRecord waystone;

        public IconGui(WaystoneRecord waystone, ServerPlayer player) {
            super(MenuType.GENERIC_3x3, player, false);
            this.waystone = waystone;
            this.updateMenu();
            this.setTitle(Component.translatable("gui.sswaystones.change_icon_title"));
        }

        private void updateMenu() {
            for (int i = 0; i < 9; i++) {
                this.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).setName(Component
                        .translatable("gui.sswaystones.change_icon_instruction").withStyle(ChatFormatting.GRAY)));
            }
            this.setSlot(4, waystone.getIconOrHead(player.level().getServer()));
        }

        @Override
        public boolean onAnyClick(int index, ClickType type, net.minecraft.world.inventory.ClickType action) {
            if (index > 8 && action.equals(net.minecraft.world.inventory.ClickType.PICKUP)) {
                if (index > 35)
                    index -= 36; // Get hotbar slot
                ItemStack stack = player.getInventory().getItem(index);

                if (stack != null && !stack.is(Items.AIR)) {
                    waystone.setIcon(stack.getItem());
                    this.close();
                }
            }
            return super.onAnyClick(index, type, action);
        }

        @Override
        public void onClose() {
            super.onClose();
            ViewerUtil.openJavaGui(player, waystone);
        }
    }

    protected static class AccessSettingsGui extends SimpleGui {
        private final WaystoneRecord waystone;

        public AccessSettingsGui(WaystoneRecord waystone, ServerPlayer player) {
            super(MenuType.GENERIC_9x3, player, false);
            this.waystone = waystone;

            this.setTitle(Component.translatable("gui.sswaystones.access_settings"));
            this.updateMenu();
        }

        private void updateMenu() {
            // Framing
            for (int i = 0; i < (9 * 3); i++) {
                this.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).setName(Component
                        .translatable("gui.sswaystones.access_settings_instruction").withStyle(ChatFormatting.GRAY)));
            }

            for (int i = 10; i < 17; i++) {
                this.clearSlot(i);
            }

            // Settings
            WaystoneRecord.AccessSettings accessSettings = waystone.getAccessSettings();
            int slot = 10;

            // Global
            if (Permissions.check(player, "sswaystones.create.global", true)) {
                GuiElementBuilder globalToggle = new GuiElementBuilder(Items.PLAYER_HEAD)
                        .setSkullOwner(IconConstants.GLOBE)
                        .setName(Component.translatable("gui.sswaystones.toggle_global")
                                .withStyle(accessSettings.isGlobal() ? ChatFormatting.GREEN : ChatFormatting.RED));

                globalToggle.setCallback((index, type, action, gui) -> {
                    accessSettings.setGlobal(!accessSettings.isGlobal());
                    this.updateMenu();
                });
                this.setSlot(slot, globalToggle);
                slot += 1;
            }

            // Team
            PlayerTeam team = player.getTeam();
            if (team != null && Permissions.check(player, "sswaystones.create.team", true)) {
                String teamName = team.getName();
                GuiElementBuilder teamToggle = new GuiElementBuilder(Items.PLAYER_HEAD)
                        .setSkullOwner(IconConstants.SHIELD)
                        .setName(Component.translatable("gui.sswaystones.toggle_team")
                                .withStyle(accessSettings.hasTeam() ? ChatFormatting.GREEN : ChatFormatting.RED));

                teamToggle.setCallback((index, type, action, gui) -> {
                    accessSettings.setTeam(accessSettings.hasTeam() ? "" : teamName);
                    this.updateMenu();
                });
                this.setSlot(slot, teamToggle);
                slot += 1;
            }

            // Server-owned
            if (Permissions.check(player, "sswaystones.create.server", 4)) {
                GuiElementBuilder serverToggle = new GuiElementBuilder(Items.PLAYER_HEAD)
                        .setSkullOwner(IconConstants.OBSERVER)
                        .setName(Component.translatable("gui.sswaystones.toggle_server")
                                .withStyle(accessSettings.isServerOwned() ? ChatFormatting.GREEN : ChatFormatting.RED));

                serverToggle.setCallback((index, type, action, gui) -> {
                    accessSettings.setServerOwned(!accessSettings.isServerOwned());
                    SquaremapIntegration.onWaystoneChanged(waystone);
                    this.updateMenu();
                });
                this.setSlot(slot, serverToggle);
                slot += 1;
            }

            // If no settings were available
            if (slot == 10) {
                this.setSlot(13,
                        new GuiElementBuilder(Items.BARRIER)
                                .setLore(List.of(Component.translatable("error.sswaystones.no_modification_permission")
                                        .withStyle(ChatFormatting.GRAY)))
                                .setName(Component.translatable("gui.back").withStyle(ChatFormatting.RED))
                                .setCallback((index, type, action, gui) -> {
                                    this.close();
                                    ViewerUtil.openJavaGui(player, waystone);
                                }));
            }
        }

        @Override
        public void onClose() {
            super.onClose();
            ViewerUtil.openJavaGui(player, waystone);
        }
    }
}
