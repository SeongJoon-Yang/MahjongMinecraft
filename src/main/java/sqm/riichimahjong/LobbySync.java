package sqm.riichimahjong;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class LobbySync {
    public static void broadcast(ServerLevel level, BlockPos pos, MahjongGame game){
        if(game.getGameState() != MahjongGame.GameState.WAITING) return;

        for(ServerPlayer player : level.getServer().getPlayerList().getPlayers()){
            if(!(player.containerMenu instanceof MahjongMenu menu) || !menu.getTablePos().equals(pos)) continue;

            Integer mySeat = game.getSeatOf(player.getUUID());
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeInt(mySeat == null ? -1 : mySeat);
            for(int seat = 0; seat < 4; seat++){
                UUID uuid = game.getUuidOfSeat(seat);
                buf.writeBoolean(uuid != null);
                if(uuid != null) buf.writeUUID(uuid);
            }
            ServerPlayNetworking.send(player,ModPackets.LOBBY_SEAT_PACKET,buf);
        }
    }
}
