package sqm.riichimahjong;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RiichiMahjong implements ModInitializer {
	public static final String MOD_ID = "riichi-mahjong";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Map<BlockPos, MahjongGame> ACTIVE_GAMES = new HashMap<>();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		ModBlocks.registerModBlocks();
		ModMenuTypes.registerModMenuTypes();	// 이런거 ai 설명 없어도 알아서 쓰고 넘어가야지 뭐 하는 거야 ㅋㅋ

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DISCARD_TILE_PACKET,((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			int index = buf.readInt();

			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if(seat == null) return;
					Tile discarded = game.discardTileAt(seat, index);
					if(discarded != null){
						if(game.checkSuukaikanDraw()){
							saveData.setDirty();
							sendGameUpdate(player,game);
							return;
						}

						// 기존 코드
						boolean callAvailable = game.canPon(0) || game.canDaiminkan(0) || !game.getChiOptionsForSeat(0).isEmpty() || game.canRon(0);
						if(!callAvailable) {
							game.clearCallState();
							// 일단 여기서 사깡유국 생각할 것 같음
							advanceDummyTurns(game, player, 0, false);
						}

						/*
						// 새 코드(예정)
						int canCallCount = 0;
						for(int i=0;i<4;i++){
							if(seat != i && game.canPon(i) || game.canDaiminkan(i) || !game.getChiOptionsForSeat(i).isEmpty() || game.canRon(i)){
								canCallCount++;
							}
						}
						if(canCallCount == 0){
							game.clearCallState();
							// 일단 여기서 사깡유국 생각할 것 같음
							advanceDummyTurns(game, player, 0, false);	// 더미 턴도 누가 사람인지 알고 넘어가야 함!
						}
						game.setCanCallCount(canCallCount);
						*/

						saveData.setDirty();
						sendGameUpdate(player,game);
					}
				}
				saveData.setDirty();
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_RIICHI_PACKET,((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			int index = buf.readInt();

			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if(seat == null) return;
					boolean success = game.declareRiichi(seat, index);
					if(success){
						if(game.checkSuukaikanDraw()){
							saveData.setDirty();
							sendGameUpdate(player,game);
							return;
						}

						// 기존 코드
						boolean callAvailable = game.canPon(0) || game.canDaiminkan(0) || !game.getChiOptionsForSeat(0).isEmpty() || game.canRon(0);
						if(!callAvailable){
							game.clearCallState();
							advanceDummyTurns(game, player, seat, false);
						}

						/*
						// 새 코드(예정)
						int canCallCount = 0;
						for(int i=0;i<4;i++){
							if(seat != i && game.canPon(i) || game.canDaiminkan(i) || !game.getChiOptionsForSeat(i).isEmpty() || game.canRon(i)){
								canCallCount++;
							}
						}
						if(canCallCount == 0){
							game.clearCallState();
							// 일단 여기서 사깡유국 생각할 것 같음
							advanceDummyTurns(game, player, 0, false);	// 더미 턴도 누가 사람인지 알고 넘어가야 함!
						}
						game.setCanCallCount(canCallCount);
						*/

						saveData.setDirty();
						sendGameUpdate(player,game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_PON_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null) {
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					// 퐁 대신 치 하면 일?시적 후리텐
					if(game.canRon(seat)){
						game.setTempFuriten(true, seat);
					}
					boolean success = game.callPon(seat);
					if (success) {
						game.clearCallState();
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_CHI_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			int idx1 = buf.readInt();
			int idx2 = buf.readInt();
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null) {
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					// 론 대신 치 하면 일?시적 후리텐
					if(game.canRon(seat)){
						game.setTempFuriten(true, seat);
					}
					boolean success = game.callChi(seat, idx1, idx2);
					if (success) {
						game.clearCallState();
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_DAIMINKAN_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					boolean success = game.callDaiminkan(seat);
					if(success){
						checkWinAndNotify(player, game); // 링샨 드로우 후 영상개화 가능성 체크
						game.clearCallState();
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_ANKAN_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			Tile tile = Tile.readFromBuf(buf);
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					boolean success = game.callAnkan(seat,tile);
					if(success) {
						checkWinAndNotify(player, game);
						game.clearCallState();
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_KAKAN_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			Tile tile = Tile.readFromBuf(buf);
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					boolean success = game.callKakan(seat,tile);
					if(success) {
						checkWinAndNotify(player, game);
						game.clearCallState();
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_TSUMO_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					boolean success = game.declareTsumo(seat);
					if(success) {
						player.sendSystemMessage(Component.literal("쯔모! " + game.calculateHan(seat) + "판"));
						int fu = game.calculateFu(seat);
						player.sendSystemMessage(Component.literal("부수: " + fu));
						player.sendSystemMessage(Component.literal("역: " + String.join(", ", game.getYaku(seat))));
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_RON_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					boolean success = game.declareRon(seat);
					if(success) {
						player.sendSystemMessage(Component.literal("론! " + game.calculateHan(seat) + "판"));
						int fu = game.calculateFu(seat);
						player.sendSystemMessage(Component.literal("부수: " + fu));
						player.sendSystemMessage(Component.literal("역: " + String.join(", ", game.getYaku(seat))));
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.PASS_CALL_PACKET, ((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if (seat == null) return;
					boolean hasExternalCall = game.canPon(seat) || game.canChi(seat) || game.canDaiminkan(seat) || game.canRon(seat);
					boolean hasSelfCall = game.canDeclareKyuushu(seat) || game.canAnkan(seat) || game.canKakan(seat) || game.canDeclareRiichi(seat) || game.canTsumo(seat);
					if(hasSelfCall || hasExternalCall) {
						if(game.canRon(seat)){
							if(game.isRiichi(seat)){
								game.setRiichiFuriten(seat);
							}else{
								game.setTempFuriten(true, seat);
							}
						}
						game.clearCallState();
						if(hasSelfCall){
							game.setSelfCallPassed(true);
						}
						if(hasExternalCall){
							if(game.checkSuukaikanDraw()){
								saveData.setDirty();
								sendGameUpdate(player, game);
								return;
							}
							advanceDummyTurns(game, player, seat, false);
						}
						saveData.setDirty();
						sendGameUpdate(player, game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.HAND_MAKE_UP,((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			boolean handMakeUp = buf.readBoolean();

			server.execute(()->{
				MahjongSavedData savedData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = savedData.getGame(tablePos);
				if(game!=null){
					game.setHandMakeUp(handMakeUp);
				}

				FriendlyByteBuf responseBuf = PacketByteBufs.create();
				responseBuf.writeBoolean(handMakeUp);
				ServerPlayNetworking.send(player, ModPackets.UPDATE_HAND_MAKE_UP, responseBuf);
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.NEXT_ROUND_PACKET,((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData savedData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = savedData.getGame(tablePos);
				if(game != null && game.canProceedToNextRound()){
					Integer seat = game.getSeatOf(player.getUUID());
					if(seat == null) return;

					game.nextRound();
					advanceDummyTurns(game,player,seat, true);

					savedData.setDirty();
					sendGameUpdate(player,game);
				}else if(game.isMatchOver()){
					RiichiMahjong.LOGGER.info("Match is over! minus score!");	// 토비 외의 경우가 있으면 바꿀 것!
					for(int i=0;i<4;i++){
						RiichiMahjong.LOGGER.info("seat {} score : {}",i,game.getScoreBySeat()[i]);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_KYUUSHU_PACKET,(((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(() -> {
				MahjongSavedData savedData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = savedData.getGame(tablePos);
				if(game != null){
					Integer seat = game.getSeatOf(player.getUUID());
					if(seat == null) return;
					boolean success = game.declareKyuushuKyuuhai(seat);
					if(success){
						savedData.setDirty();
						sendGameUpdate(player,game);
					}
				}
			});
		})));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.MATCH_START_PACKET,((((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData savedData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = savedData.getGame(tablePos);
				if(game != null){
					boolean success = game.startGame();
					if(success){
						savedData.setDirty();
						sendGameUpdate(player,game);
					}
				}
			});
		}))));
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	public static void sendGameUpdate(net.minecraft.server.level.ServerPlayer player, MahjongGame game){
		int playerSeat = game.getSeatOf(player.getUUID());

		game.switchToSeat(playerSeat);

		FriendlyByteBuf responseBuf = PacketByteBufs.create();
		responseBuf.writeEnum(game.getGameState());
		responseBuf.writeInt(game.getHand().size());
		for(Tile t : game.getHand()){
			t.writeToBuf(responseBuf);
		}
		responseBuf.writeInt(game.getDiscarded().size());
		for(Tile t : game.getDiscarded()){
			t.writeToBuf(responseBuf);
		}
		// 디버그용 나중에 수정될 수 있음
		responseBuf.writeInt(4);
		for(int seat=0;seat<4;seat++){
			List<Tile> seatDiscarded = game.getDiscardedOfSeat(seat);
			responseBuf.writeInt(seatDiscarded.size());
			for(Tile tile : seatDiscarded) tile.writeToBuf(responseBuf);
		}
		responseBuf.writeInt(game.getMelds().size());
		for(int i=0;i<game.getMelds().size();i++){
			responseBuf.writeEnum(game.getMeldTypes().get(i));
			List<Tile> meld = game.getMelds().get(i);
			responseBuf.writeInt(meld.size());
			for(Tile t : meld){
				t.writeToBuf(responseBuf);
			}
		}
		for(int i=0;i<4;i++){
			responseBuf.writeBoolean(game.isRiichi(i));
		}
		responseBuf.writeInt(game.getWallSize());
		responseBuf.writeInt(game.getRoundWind());
		responseBuf.writeBoolean(game.isFuriten(playerSeat));

		List<Tile> doraIndicators = game.getRevealedDoraIndicators();
		responseBuf.writeInt(doraIndicators.size());
		for(Tile t : doraIndicators) t.writeToBuf(responseBuf);
		RiichiMahjong.LOGGER.info("뒷도라 전송됨? gameEnd={},win={},riichi={},개수={}",
				game.isGameEnd(),game.isWin(),game.isRiichi(playerSeat),game.getRevealedUraDoraIndicators().size());
		List<Tile> uraDoraIndicators = (game.isGameEnd() && game.isWin() && game.isRiichi(playerSeat))
				? game.getRevealedUraDoraIndicators()
				: new ArrayList<>();
		responseBuf.writeInt(uraDoraIndicators.size());
		for(Tile tile : uraDoraIndicators) tile.writeToBuf(responseBuf);

		responseBuf.writeBoolean(game.isHandMakeUp());
		responseBuf.writeBoolean(game.isGameEnd());
		responseBuf.writeBoolean(game.isMatchOver());
		responseBuf.writeBoolean(game.isWin());
		responseBuf.writeBoolean(game.isTsumo());
		responseBuf.writeInt(game.getTurnOrder());
		responseBuf.writeInt(game.getDealerSeat());
		// responseBuf.writeInt(game.getCanCallCount());	// 일단 추가 안하고 주석처리
		// responseBuf.writeint(game.getDeclareCallCount());	// 일단 추가 안하고 주석처리

		responseBuf.writeInt(game.getWinSeat());
		responseBuf.writeInt(game.getWinHan());
		responseBuf.writeInt(game.getWinFu());
		responseBuf.writeInt(game.getWinScores());
		responseBuf.writeUtf(game.getWinLabel());

		Tile winningTile = game.getWinningTile();
		responseBuf.writeBoolean(winningTile != null);
		if(winningTile != null) winningTile.writeToBuf(responseBuf);

		List<Map.Entry<String,Integer>> entries = game.getYakuResultEntries(playerSeat);
		responseBuf.writeInt(entries.size());
		for(Map.Entry<String,Integer> e : entries){
			responseBuf.writeUtf(e.getKey());
			responseBuf.writeInt(e.getValue());
		}

		responseBuf.writeBoolean(game.canDeclareRiichi(playerSeat));
		responseBuf.writeBoolean(game.canPon(playerSeat));
		responseBuf.writeBoolean(game.canDaiminkan(playerSeat));

		List<List<Integer>> chiOptions = game.getChiOptionsForSeat(playerSeat);
		responseBuf.writeInt(chiOptions.size());
		for(List<Integer> option : chiOptions){
			responseBuf.writeInt(option.get(0));
			responseBuf.writeInt(option.get(1));
		}

		List<Tile> ankanOptions = game.getAnkanOptions(playerSeat);
		responseBuf.writeInt(ankanOptions.size());
		for(Tile tile : ankanOptions) tile.writeToBuf(responseBuf);

		List<Tile> kakanOptions = game.getKakanOptions(playerSeat);
		responseBuf.writeInt(kakanOptions.size());
		for(Tile tile : kakanOptions) tile.writeToBuf(responseBuf);

		responseBuf.writeBoolean(game.canTsumo(playerSeat));
		responseBuf.writeBoolean(game.canRon(playerSeat));
		responseBuf.writeBoolean(game.canDeclareKyuushu(playerSeat));

		responseBuf.writeBoolean(game.getDrawReason() != null);
		if(game.getDrawReason() != null) {
			responseBuf.writeUtf(game.getDrawReason());
		}

		int[] scores = game.getScoreBySeat();
		for(int i=0;i<4;i++){
			responseBuf.writeInt(scores[i]);
		}

		int[] settleScores = game.getSettleScoreBySeat();
		for(int i=0;i<4;i++){
			responseBuf.writeInt(settleScores[i]);
		}

		ServerPlayNetworking.send(player, ModPackets.UPDATE_HAND_PACKET, responseBuf);
	}

	public static void checkWinAndNotify(net.minecraft.server.level.ServerPlayer player, MahjongGame game) {
		int seat = game.getSeatOf(player.getUUID());
		if (game.checkWinCondition(seat)) {
			player.sendSystemMessage(Component.literal("승리! 화료했습니다!"));
			game.setGameEnd(seat);
			List<String> yaku = game.getYaku(seat);
			player.sendSystemMessage(Component.literal("역: " + String.join(", ", yaku)));
			player.sendSystemMessage(Component.literal("판수: " + game.calculateHan(seat) + "판"));
		}
	}

	private static void advanceDummyTurns(MahjongGame game, ServerPlayer player, int humanSeat, boolean dealTile){
		boolean skipDraw = dealTile;

		while(!game.isGameEnd() && game.getTurnOrder() != humanSeat){
			int seat = game.getTurnOrder();

			RiichiMahjong.LOGGER.info("seat : {}, humanseat : {}",seat, humanSeat);

			if(game.isSeatAssigned(seat)){
//				if(!skipDraw) game.drawTile(seat);	// 이렇게 써도 될 것 같긴 한데 이건 다인용일 테스트할 때 쓰는거로
				return;
			}

			if(!skipDraw) {
				Tile drawn = game.drawTile(seat);
				if (drawn == null) return;
			}

			skipDraw = false;

			int lastIndex = game.getHandSizeOfSeat(seat) - 1;
			game.discardTileAt(seat, lastIndex);

			boolean callAvailable = game.canPon(humanSeat) || game.canChi(humanSeat)
					|| game.canDaiminkan(humanSeat) || game.canRon(humanSeat);
			RiichiMahjong.LOGGER.info("advanceDummyTurns: seat={}, turnOrder(after discard)={}, canChi(humanSeat)={}, callAvailable={}",
					seat, game.getTurnOrder(), game.canChi(humanSeat), callAvailable);
			if(callAvailable) return;
			game.clearCallState();
		}

		if(!game.isGameEnd() && game.getTurnOrder() == humanSeat && !skipDraw){
			game.drawTile(humanSeat);
		}

		for(int seat=0;seat<4;seat++){
			List<Tile> discarded = game.getDiscardedOfSeat(seat);
			String label = "discarded : ";
			for(Tile tile : discarded) label += tile.getTextureId() + " ";
			RiichiMahjong.LOGGER.info(label);
		}
	}
}
