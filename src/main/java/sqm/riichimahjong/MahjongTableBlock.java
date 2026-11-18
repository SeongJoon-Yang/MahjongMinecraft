package sqm.riichimahjong;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MahjongTableBlock extends Block {

    public MahjongTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            MahjongSavedData savedData = MahjongSavedData.get(serverLevel);

            // ===== 임시 디버그 코드: 한 번 실행 후 반드시 삭제 =====
            // 나중에 실제 서버에서도 잘 작동하는지 확인하고 이상하게 돌아가면 이거 다시 켜야 함
//            MahjongGame debugGame = savedData.getGame(pos);
//            if (debugGame != null) {
//                debugGame.matchOverTester(true);
//                savedData.setDirty();
//            }
            // ===== 여기까지 삭제 =====

//            MahjongGame game = RiichiMahjong.ACTIVE_GAMES.computeIfAbsent(pos, p -> new MahjongGame());
            MahjongGame game = savedData.getGame(pos);
            if(game == null || game.isMatchOver()){
                game = new MahjongGame();
                savedData.setGame(pos,game);
            }
            final MahjongGame finalGame = game;

            int seat = finalGame.assignSeat(player.getUUID());
            final int finalSeat = seat < 0 ? -1 : seat;
            finalGame.switchToSeat(finalSeat);

            List<Tile> playerHand = game.getHand();
            List<Tile> discardedTiles = game.getDiscarded();

            RiichiMahjong.LOGGER.info("gameState : {}",finalGame.getGameState());
            RiichiMahjong.LOGGER.info("player UUID : {}",player.getUUID());
            RiichiMahjong.LOGGER.info("playerSeat : {}",seat);
            RiichiMahjong.LOGGER.info("turnOrder : {}",finalGame.getTurnOrder());

            // 메뉴 열기
            player.openMenu(new ExtendedScreenHandlerFactory() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("마작 탁자");
                }

                @Override
                public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                    return new MahjongMenu(syncId,playerHand,discardedTiles, pos);
                }

                @Override
                public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
                    int playerSeat = finalGame.getSeatOf(player.getUUID());

                    buf.writeEnum(finalGame.getGameState());
                    buf.writeInt(playerHand.size());
                    for (Tile tile : playerHand) {
                        tile.writeToBuf(buf);
                    }
                    buf.writeInt(discardedTiles.size());
                    for (Tile tile : discardedTiles){
                        tile.writeToBuf(buf);
                    }
                    // 디버그용 나중에 수정될 수 있음
                    buf.writeInt(4);
                    for(int seat=0;seat<4;seat++){
                        List<Tile> seatDiscarded = finalGame.getDiscardedOfSeat(seat);
                        buf.writeInt(seatDiscarded.size());
                        for(Tile tile : seatDiscarded) tile.writeToBuf(buf);
                    }
                    buf.writeInt(finalGame.getMelds().size());
                    for(int i=0;i<finalGame.getMelds().size();i++){
                        buf.writeEnum(finalGame.getMeldTypes().get(i));
                        List<Tile> meld = finalGame.getMelds().get(i);
                        buf.writeInt(meld.size());
                        for(Tile t : meld){
                            t.writeToBuf(buf);
                        }
                    }
                    for(int i=0;i<4;i++){
                        buf.writeBoolean(finalGame.isRiichi(i));
                    }
                    buf.writeInt(finalGame.getWallSize());
                    buf.writeInt(finalGame.getRoundWind());
                    buf.writeBoolean(finalGame.isFuriten(finalSeat));

                    List<Tile> doraIndicators = finalGame.getRevealedDoraIndicators();
                    buf.writeInt(doraIndicators.size());
                    for(Tile t : doraIndicators) t.writeToBuf(buf);

                    buf.writeBlockPos(pos);
                    buf.writeBoolean(finalGame.isHandMakeUp());
                    buf.writeBoolean(finalGame.isGameEnd());
                    buf.writeBoolean(finalGame.isMatchOver());
                    buf.writeBoolean(finalGame.isWin());
                    buf.writeBoolean(finalGame.isTsumo());
                    buf.writeInt(finalGame.getTurnOrder());
                    buf.writeInt(finalGame.getDealerSeat());
                    buf.writeBoolean(finalGame.getCallChoices()[playerSeat] == CallChoice.PENDING);

                    Tile winningTile = finalGame.getWinningTile();
                    buf.writeBoolean(winningTile != null);
                    if(winningTile != null) winningTile.writeToBuf(buf);

                    buf.writeBoolean(finalGame.canDeclareRiichi(finalSeat));
                    buf.writeBoolean(finalGame.canPon(finalSeat));
                    buf.writeBoolean(finalGame.canDaiminkan(finalSeat));

                    List<List<Integer>> chiOptions = finalGame.getChiOptionsForSeat(finalSeat);
                    buf.writeInt(chiOptions.size());
                    for(List<Integer> option : chiOptions){
                        buf.writeInt(option.get(0));
                        buf.writeInt(option.get(1));
                    }

                    List<Tile> ankanOptions = finalGame.getAnkanOptions(finalSeat);
                    buf.writeInt(ankanOptions.size());
                    for(Tile t : ankanOptions) t.writeToBuf(buf);

                    List<Tile> kakanOptions = finalGame.getKakanOptions(finalSeat);
                    buf.writeInt(kakanOptions.size());
                    for(Tile t : kakanOptions) t.writeToBuf(buf);

                    buf.writeBoolean(finalGame.canTsumo(finalSeat));
                    buf.writeBoolean(finalGame.canRon(finalSeat));
                    buf.writeBoolean(finalGame.canDeclareKyuushu(finalSeat));

                    buf.writeBoolean(finalGame.getDrawReason() != null);
                    if(finalGame.getDrawReason() != null) {
                        buf.writeUtf(finalGame.getDrawReason());
                    }

                    int[] scores = finalGame.getScoreBySeat();
                    for(int i=0;i<4;i++){
                        buf.writeInt(scores[i]);
                    }

                    int[] settleScores = finalGame.getSettleScoreBySeat();
                    for(int i=0;i<4;i++){
                        buf.writeInt(settleScores[i]);
                    }

                    boolean winGameEnd = finalGame.isGameEnd() && finalGame.isWin();
                    buf.writeBoolean(winGameEnd);
                    if(winGameEnd){
                        // 패킷을 보내도 의도한 대로 작동하지 않음
                        // RiichiMahjong.sendWinGameUpdate(player,finalGame);

                        int lastWinSeat = finalGame.getWinSeat();
                        buf.writeInt(lastWinSeat);

                        if(lastWinSeat < 0) return;

                        finalGame.switchToSeat(lastWinSeat);

                        buf.writeInt(finalGame.getHand().size());
                        for(Tile tile : finalGame.getHand()){
                            tile.writeToBuf(buf);
                        }

                        buf.writeInt(finalGame.getMelds().size());
                        for(int i=0;i<finalGame.getMelds().size();i++){
                            buf.writeEnum(finalGame.getMeldTypes().get(i));
                            List<Tile> meld = finalGame.getMelds().get(i);
                            buf.writeInt(meld.size());
                            for(Tile t : meld){
                                t.writeToBuf(buf);
                            }
                        }

                        List<Tile> uraDoraIndicators = (finalGame.isGameEnd() && finalGame.isWin() && finalGame.isRiichi(lastWinSeat))
                                ? finalGame.getRevealedUraDoraIndicators()
                                : new ArrayList<>();
                        buf.writeInt(uraDoraIndicators.size());
                        for(Tile tile : uraDoraIndicators) tile.writeToBuf(buf);

                        buf.writeInt(finalGame.getWinHan());
                        buf.writeInt(finalGame.getWinFu());
                        buf.writeInt(finalGame.getWinScores());
                        buf.writeUtf(finalGame.getWinLabel());

                        List<Map.Entry<String,Integer>> entries = finalGame.getYakuResultEntries(lastWinSeat);
                        buf.writeInt(entries.size());
                        for(Map.Entry<String,Integer> e : entries){
                            buf.writeUtf(e.getKey());
                            buf.writeInt(e.getValue());
                        }
                    }
                }
            });

            LobbySync.broadcast(serverLevel,pos,game);
        }
        return InteractionResult.SUCCESS;
    }
}

// 사실 코드는 ai 돌리는 거임