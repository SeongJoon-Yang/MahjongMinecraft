package sqm.riichimahjong;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class MahjongSavedData extends SavedData {
    private final Map<BlockPos, MahjongGame> games = new HashMap<>();

    public static MahjongSavedData get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(
                MahjongSavedData::load,
                MahjongSavedData::new,
                "riichi_mahjong_games"
        );
    }

    public MahjongGame getGame(BlockPos pos){
        return games.get(pos);
    }

    public Map<BlockPos, MahjongGame> getGames(){
        return games;
    }

    public void setGame(BlockPos pos, MahjongGame game){
        games.put(pos,game);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag){
        ListTag list = new ListTag();
        for(Map.Entry<BlockPos, MahjongGame> entry : games.entrySet()){
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x",entry.getKey().getX());
            entryTag.putInt("y",entry.getKey().getY());
            entryTag.putInt("z",entry.getKey().getZ());
            entryTag.put("game",entry.getValue().toNBT());
            list.add(entryTag);
        }
        tag.put("games",list);
        return tag;
    }

    public static MahjongSavedData load(CompoundTag tag){
        MahjongSavedData data = new MahjongSavedData();
        ListTag list = tag.getList("games", 10);
        for(int i=0;i<list.size();i++){
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = new BlockPos(entryTag.getInt("x"),entryTag.getInt("y"),entryTag.getInt("z"));
            MahjongGame game = MahjongGame.fromNBT(entryTag.getCompound("game"));
            data.games.put(pos,game);
        }
        return data;
    }
}
