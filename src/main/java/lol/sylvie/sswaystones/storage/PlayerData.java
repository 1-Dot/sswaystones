/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    public static final int MAX_FAVORITES = 45; // One page of waystones

    public ArrayList<String> discoveredWaystones;
    public ArrayList<String> favoriteWaystones;

    public PlayerData() {
        this(new ArrayList<>(), new ArrayList<>());
    }

    public PlayerData(List<String> discoveredWaystones) {
        this(discoveredWaystones, new ArrayList<>());
    }

    public PlayerData(List<String> discoveredWaystones, List<String> favoriteWaystones) {
        this.discoveredWaystones = new ArrayList<>(discoveredWaystones);
        this.favoriteWaystones = new ArrayList<>(favoriteWaystones);
    }

    public List<String> getDiscoveredWaystones() {
        return discoveredWaystones;
    }

    public List<String> getFavoriteWaystones() {
        return favoriteWaystones;
    }

    public boolean isFavorite(String hash) {
        return favoriteWaystones.contains(hash);
    }

    public boolean toggleFavorite(String hash) {
        if (favoriteWaystones.contains(hash)) {
            favoriteWaystones.remove(hash);
            return false;
        } else if (favoriteWaystones.size() < MAX_FAVORITES) {
            favoriteWaystones.add(hash);
            return true;
        }
        return false; // Cannot add more favorites
    }

    public boolean canAddFavorite() {
        return favoriteWaystones.size() < MAX_FAVORITES;
    }

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(Codec.STRING.listOf().fieldOf("discovered_waystones").forGetter(PlayerData::getDiscoveredWaystones),
                    Codec.STRING.listOf().optionalFieldOf("favorite_waystones", new ArrayList<>())
                            .forGetter(PlayerData::getFavoriteWaystones))
            .apply(instance, PlayerData::new));

}
