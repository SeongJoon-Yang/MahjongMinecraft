package sqm.riichimahjong;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
							sendGameUpdateToAll(player,game);
							return;
						}

						game.openCallWindow();
						if(!game.hasPendingCall()){
							game.clearCallWindow();
							advanceDummyTurns(game, seat, false);
						}
						RiichiMahjong.LOGGER.info("Call Window : {}",java.util.Arrays.toString(game.getCallChoices()));

						saveData.setDirty();
						sendGameUpdateToAll(player,game);
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
							sendGameUpdateToAll(player,game);
							return;
						}

						// 새 코드(예정)
						game.openCallWindow();
						if(!game.hasPendingCall()){
							game.clearCallWindow();
							advanceDummyTurns(game, seat, false);
						}
						RiichiMahjong.LOGGER.info("Call Window : {}",java.util.Arrays.toString(game.getCallChoices()));

						saveData.setDirty();
						sendGameUpdateToAll(player,game);
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
						game.clearCallWindow();
						saveData.setDirty();
						sendGameUpdateToAll(player,game);
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
						game.clearCallWindow();
						saveData.setDirty();
						sendGameUpdateToAll(player,game);
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
						game.clearCallWindow();
						saveData.setDirty();
						sendGameUpdateToAll(player,game);
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
						sendGameUpdateToAll(player,game);
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
						sendGameUpdateToAll(player,game);
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
						saveData.setDirty();
						sendGameUpdateToAll(player,game);
						sendWinGameUpdateToAll(player,game);
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
						game.clearCallWindow();
						saveData.setDirty();
						sendGameUpdateToAll(player,game);
						sendWinGameUpdateToAll(player,game);
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
							game.clearCallWindow();
							if(game.checkSuukaikanDraw()){
								saveData.setDirty();
								sendGameUpdateToAll(player,game);
								return;
							}
							advanceDummyTurns(game, seat, false);
						}
						saveData.setDirty();
						sendGameUpdateToAll(player,game);
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

					game.setCallNextRound(seat);
					boolean[] callNextRound = game.getCallNextRound();
					for(int i=0;i<4;i++){
						// 유저가 앉아있는 자리인데 확인을 누르지 않은 경우
						if(game.isSeatAssigned(i) && !callNextRound[i] && !game.getSeatAway()[i]){
							return;
						}
					}

					game.nextRound();
					advanceDummyTurns(game, seat, true);

					savedData.setDirty();
					sendGameUpdateToAll(player,game);
				}else if(game != null && game.isMatchOver()){
					RiichiMahjong.LOGGER.info("Match is over! minus score!");	// 토비 외의 경우가 있으면 바꿀 것!
					for(int i=0;i<4;i++){
						RiichiMahjong.LOGGER.info("seat {} score : {}",i,game.getScoreBySeat()[i]);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.DECLARE_KYUUSHU_PACKET,((server, player, handler, buf, responseSender) -> {
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
						sendGameUpdateToAll(player,game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.MATCH_START_PACKET,((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			server.execute(()->{
				MahjongSavedData savedData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = savedData.getGame(tablePos);
				if(game != null){
					// seat가 null이거나 0이 아니면 시작 패킷 무시
					Integer seat = game.getSeatOf(player.getUUID());
					if(seat == null || seat != 0) return;
					// game 상태가 대기 상태가 아니면 시작 패킷 무시
					if(game.getGameState() != MahjongGame.GameState.WAITING) return;
					boolean success = game.startGame();
					if(success){
						savedData.setDirty();
						sendGameUpdateToAll(player,game);
					}
				}
			});
		}));

		ServerPlayNetworking.registerGlobalReceiver(ModPackets.PENDING_CALL_PACKET,((server, player, handler, buf, responseSender) -> {
			BlockPos tablePos = buf.readBlockPos();
			CallChoice choice = buf.readEnum(CallChoice.class);
			int chiIndex = buf.readInt();
			server.execute(() -> {
				MahjongSavedData saveData = MahjongSavedData.get(player.serverLevel());
				MahjongGame game = saveData.getGame(tablePos);
				if(game != null){
					int seat = game.getSeatOf(player.getUUID());
					if(seat < 0) return;	// 게임 좌석에 앉아있지 않은 경우
					if(choice == CallChoice.NONE || choice == CallChoice.PENDING) return;	// none이나 pending을 보내면 거부하기
					if(game.getCallChoices()[seat] != CallChoice.PENDING) return;	// CallChoice가 Pending이 아니면 넘김
					if(choice == CallChoice.RON) { if(!game.canRon(seat)) return; }
					else if(choice == CallChoice.PON) { if(!game.canPon(seat)) return; }
					else if(choice == CallChoice.DAIMINKAN) { if(!game.canDaiminkan(seat)) return; }
					else if(choice == CallChoice.CHI) {
						if(chiIndex < 0 || chiIndex >= game.getChiOptionsForSeat(seat).size() || game.getChiOptionsForSeat(seat).isEmpty()) return;
					}
					game.setCallChoice(seat, choice, chiIndex);

					CallChoice[] callChoices = game.getCallChoices();
					for(int i=0;i<4;i++){
						if(callChoices[i] == CallChoice.PENDING){
							saveData.setDirty();
							sendGameUpdate(player, game);
							return;
						}
					}

					boolean callResolved = game.resolveCalls();
					if(!callResolved) advanceDummyTurns(game, seat, false);
					saveData.setDirty();
					sendGameUpdateToAll(player,game);
					if(game.isGameEnd() && game.isWin()){
						sendWinGameUpdateToAll(player,game);
					}
				}
			});
		}));

		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if(world.getGameTime() % 5 != 0) return;	// 5틱에 한 번
			tick(world);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MahjongGame.serverTick = server.getTickCount();
		});
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

		responseBuf.writeBoolean(game.isHandMakeUp());
		responseBuf.writeBoolean(game.isGameEnd());
		responseBuf.writeBoolean(game.isMatchOver());
		responseBuf.writeBoolean(game.isWin());
		responseBuf.writeBoolean(game.isTsumo());
		responseBuf.writeInt(game.getTurnOrder());
		responseBuf.writeInt(game.getDealerSeat());
		responseBuf.writeBoolean(game.getCallChoices()[playerSeat] == CallChoice.PENDING);

		Tile winningTile = game.getWinningTile();
		responseBuf.writeBoolean(winningTile != null);
		if(winningTile != null) winningTile.writeToBuf(responseBuf);

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

		int[] riichiDiscardIndexBySeat = game.getRiichiDiscardIndexBySeat();
		for(int i=0;i<4;i++){
			responseBuf.writeInt(riichiDiscardIndexBySeat[i]);
		}

		long[] bankLeft = game.getBankLeft();
		for(int i=0;i<4;i++){
			responseBuf.writeLong(bankLeft[i]);
		}

		ServerPlayNetworking.send(player, ModPackets.UPDATE_HAND_PACKET, responseBuf);
	}

	private static void sendGameUpdateToAll(ServerPlayer actor, MahjongGame game){
		if(!(actor.containerMenu instanceof MahjongMenu menu)) return;   // 마작 메뉴가 아니면 그냥 끝내기
		BlockPos pos = menu.getTablePos();
		for(ServerPlayer p : actor.server.getPlayerList().getPlayers()){
			if(p.containerMenu instanceof MahjongMenu m && m.getTablePos().equals(pos)){
				sendGameUpdate(p, game);   // 안에서 받는 사람의 좌석으로 시점을 맞춰요
			}
		}
	}

	// 자동타패
	private static void sendGameUpdateToAll(MinecraftServer server, BlockPos pos, MahjongGame game){
		for(ServerPlayer p : server.getPlayerList().getPlayers()){
			if(p.containerMenu instanceof MahjongMenu m && m.getTablePos().equals(pos)){
				sendGameUpdate(p, game);   // 안에서 받는 사람의 좌석으로 시점을 맞춰요
			}
		}
	}

	public static void sendWinGameUpdate(ServerPlayer player, MahjongGame game){
		FriendlyByteBuf responseBuf = PacketByteBufs.create();

		int lastWinSeat = game.getWinSeat();
		responseBuf.writeInt(lastWinSeat);

		if(lastWinSeat < 0) return;

		// 화료자 시점으로 변경
		game.switchToSeat(lastWinSeat);

		responseBuf.writeInt(game.getHand().size());
		for(Tile tile : game.getHand()){
			tile.writeToBuf(responseBuf);
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

		List<Tile> uraDoraIndicators = (game.isGameEnd() && game.isWin() && game.isRiichi(lastWinSeat))
				? game.getRevealedUraDoraIndicators()
				: new ArrayList<>();
		responseBuf.writeInt(uraDoraIndicators.size());
		for(Tile tile : uraDoraIndicators) tile.writeToBuf(responseBuf);

		responseBuf.writeInt(game.getWinHan());
		responseBuf.writeInt(game.getWinFu());
		responseBuf.writeInt(game.getWinScores());
		responseBuf.writeUtf(game.getWinLabel());

		List<Map.Entry<String,Integer>> entries = game.getYakuResultEntries(lastWinSeat);
		responseBuf.writeInt(entries.size());
		for(Map.Entry<String,Integer> e : entries){
			responseBuf.writeUtf(e.getKey());
			responseBuf.writeInt(e.getValue());
		}

		ServerPlayNetworking.send(player, ModPackets.WIN_GAME_UPDATE_PACKET ,responseBuf);
	}

	private static void sendWinGameUpdateToAll(ServerPlayer actor, MahjongGame game){
		if(!(actor.containerMenu instanceof MahjongMenu menu)) return;
		BlockPos pos = menu.getTablePos();
		for(ServerPlayer p : actor.server.getPlayerList().getPlayers()){
			if(p.containerMenu instanceof MahjongMenu m && m.getTablePos().equals(pos)){
				sendWinGameUpdate(p, game);
			}
		}
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

	private static void advanceDummyTurns(MahjongGame game, int humanSeat, boolean dealTile){
		boolean skipDraw = dealTile;

		while(!game.isGameEnd() && game.getTurnOrder() != humanSeat){
			int seat = game.getTurnOrder();

			RiichiMahjong.LOGGER.info("seat : {}, humanseat : {}",seat, humanSeat);

			if(game.isSeatAssigned(seat)){
				if(!skipDraw) game.drawTile(seat);	// 이렇게 써도 될 것 같긴 한데 이건 다인용일 테스트할 때 쓰는거로
				return;
			}

			if(!skipDraw) {
				Tile drawn = game.drawTile(seat);
				if (drawn == null) return;
			}

			skipDraw = false;

			int lastIndex = game.getHandSizeOfSeat(seat) - 1;
			game.discardTileAt(seat, lastIndex);

			game.openCallWindow();
			if(game.hasPendingCall()){
				return;
			}
			game.clearCallWindow();
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

	private static void tick(ServerLevel world){
		RiichiMahjong.LOGGER.info("world tick event");
		long now = world.getGameTime();
		RiichiMahjong.LOGGER.info("serverTick(now) : {}",now);
		MahjongSavedData data = MahjongSavedData.get(world);
		for(Map.Entry<BlockPos, MahjongGame> e : new ArrayList<>(data.getGames().entrySet())){
			MahjongGame game = e.getValue();

			if(game.getGameState() != MahjongGame.GameState.PLAYING) continue;
			if(!game.hasAnyonePresent()) continue;
			boolean line = false;
			long[] deadline = game.getTurnDeadline();
			for(int i=0;i<4;i++){
                if (deadline[i] > 0 && now >= deadline[i]) {
                    line = true;
                    break;
                }
			}
			if(!line) continue;

			if(game.hasPendingCall()){
				CallChoice[] callChoices = game.getCallChoices();
				for(int i=0;i<4;i++){
					if (deadline[i] > 0 && now >= deadline[i] && callChoices[i] == CallChoice.PENDING){
						game.setCallChoice(i,CallChoice.PASS,-1);
						RiichiMahjong.LOGGER.info("call has passed!");
						RiichiMahjong.LOGGER.info("serverTick(now) : {}",now);
						RiichiMahjong.LOGGER.info("seat : {}",i);
					}
				}
			}else{
				int turnSeat = game.getTurnOrder();
				int handSize = 14 - 3 * game.getMelds().size();		// 문제 생길 수도 있음
				if (deadline[turnSeat] > 0 && now >= deadline[turnSeat]){
					Tile discarded = game.discardTileAt(turnSeat,handSize - 1);
					// ai가 쿠이카에 얘기해서 일단 넣어놓음
					int tryDiscard = handSize - 2;
					while(discarded == null){
						discarded = game.discardTileAt(turnSeat, tryDiscard);
						tryDiscard--;
					}
					RiichiMahjong.LOGGER.info("tile has discarded!");
					RiichiMahjong.LOGGER.info("serverTick(now) : {}", now);
					RiichiMahjong.LOGGER.info("seat : {}", turnSeat);
					if(game.checkSuukaikanDraw()){
						data.setDirty();
						sendGameUpdateToAll(world.getServer(),e.getKey(),game);
						return;
					}

					game.openCallWindow();
					if(!game.hasPendingCall()){
						game.clearCallWindow();
						advanceDummyTurns(game, turnSeat, false);
					}
					RiichiMahjong.LOGGER.info("Call Window : {}",java.util.Arrays.toString(game.getCallChoices()));

					data.setDirty();
					sendGameUpdateToAll(world.getServer(),e.getKey(),game);
				}
			}
		}
	}
}
