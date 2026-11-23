package sqm.riichimahjong.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import sqm.riichimahjong.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RiichiMahjongClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		MenuScreens.register(ModMenuTypes.MAHJONG_MENU, MahjongScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(ModPackets.UPDATE_HAND_PACKET,((client, handler, buf, responseSender) -> {
			MahjongGame.GameState state = buf.readEnum(MahjongGame.GameState.class);
			int size = buf.readInt();
            List<Tile> newHand = new ArrayList<>();
			for(int i=0;i<size;i++){
				newHand.add(Tile.readFromBuf(buf));
			}

			int discardSize = buf.readInt();
			List<Tile> newDiscarded = new ArrayList<>();
			for(int i=0;i<discardSize;i++){
				newDiscarded.add(Tile.readFromBuf(buf));
			}

			int seatCount = buf.readInt();
			List<List<Tile>> allDiscarded = new ArrayList<>();
			for(int i=0;i<seatCount;i++){
				int seatDiscardedSize = buf.readInt();
				List<Tile> seatTiles = new ArrayList<>();
				for(int j=0;j<seatDiscardedSize;j++){
					seatTiles.add(Tile.readFromBuf(buf));
				}
				allDiscarded.add(seatTiles);
			}

			int meldCount = buf.readInt();
			List<List<Tile>> newMelds = new ArrayList<>();
			List<MeldType> newMeldTypes = new ArrayList<>();
			for(int i=0;i<meldCount;i++){
				MeldType type = buf.readEnum(MeldType.class);
				int tileCount = buf.readInt();
				List<Tile> meld = new ArrayList<>();
				for(int j=0;j<tileCount;j++){
					meld.add(Tile.readFromBuf(buf));
				}
				newMelds.add(meld);
				newMeldTypes.add(type);
			}
			boolean[] isRiichi = new boolean[4];
			for(int i=0;i<4;i++){
				isRiichi[i] = buf.readBoolean();
			}
			int wallSize = buf.readInt();
			int roundWind = buf.readInt();
			boolean isFuriten = buf.readBoolean();

			int doraCount = buf.readInt();
			List<Tile> newDoraIndicator = new ArrayList<>();
			for(int i=0;i<doraCount;i++){
				newDoraIndicator.add(Tile.readFromBuf(buf));
			}

			boolean handMakeUp = buf.readBoolean();
			boolean gameEnd = buf.readBoolean();
			boolean matchOver = buf.readBoolean();
			boolean win = buf.readBoolean();
			boolean isTsumo = buf.readBoolean();
			int turnOrder = buf.readInt();
			int dealerSeat = buf.readInt();
			boolean myCallPending = buf.readBoolean();

			boolean hasWinningTile = buf.readBoolean();
			Tile winningTile = hasWinningTile ? Tile.readFromBuf(buf) : null;

			boolean canRiichi = buf.readBoolean();
			boolean canPon = buf.readBoolean();
			boolean canDaiminkan = buf.readBoolean();

			int chiCount = buf.readInt();
			List<List<Integer>> chiOptions = new ArrayList<>();
			for(int i=0;i<chiCount;i++){
				int idx1 = buf.readInt();
				int idx2 = buf.readInt();
				chiOptions.add(List.of(idx1, idx2));
			}

			int ankanCount = buf.readInt();
			List<Tile> ankanOptions = new ArrayList<>();
			for(int i=0;i<ankanCount;i++){
				ankanOptions.add(Tile.readFromBuf(buf));
			}

			int kakanCount = buf.readInt();
			List<Tile> kakanOptions = new ArrayList<>();
			for(int i=0;i<kakanCount;i++){
				kakanOptions.add(Tile.readFromBuf(buf));
			}

			boolean canTsumo = buf.readBoolean();
			boolean canRon = buf.readBoolean();
			boolean canDeclareKyuushu = buf.readBoolean();

			boolean isDraw = buf.readBoolean();
			String drawReason = isDraw ? buf.readUtf() : null;

			int[] scores = new int[4];
			for(int i=0;i<4;i++){
				scores[i] = buf.readInt();
			}

			int[] settleScores = new int[4];
			for(int i=0;i<4;i++){
				settleScores[i] = buf.readInt();
			}

			int[] riichiDiscardIndexBySeat = new int[4];
			for(int i=0;i<4;i++){
				riichiDiscardIndexBySeat[i] = buf.readInt();
			}

			long[] bankLeft = new long[4];
			for(int i=0;i<4;i++){
				bankLeft[i] = buf.readLong();
			}

			client.execute(()->{
				if(client.player.containerMenu instanceof MahjongMenu mahjongMenu){
					mahjongMenu.setGameState(state);
					mahjongMenu.updateHand(newHand);
					mahjongMenu.updateDiscarded(newDiscarded);
					mahjongMenu.setAllDiscarded(allDiscarded);
					mahjongMenu.updateMelds(newMelds, newMeldTypes);
					mahjongMenu.updateRiichi(isRiichi, riichiDiscardIndexBySeat);
					mahjongMenu.setBankLeft(bankLeft);
					mahjongMenu.updateWallSize(wallSize);
					mahjongMenu.updateRoundWind(roundWind);
					mahjongMenu.setFuriten(isFuriten);
					mahjongMenu.updateDora(newDoraIndicator);
					mahjongMenu.setHandMakeUp(handMakeUp);
					mahjongMenu.setGameEnd(gameEnd);
					mahjongMenu.setMatchOver(matchOver);
					mahjongMenu.setWin(win);
					mahjongMenu.setTsumo(isTsumo);
					mahjongMenu.setTurnOrder(turnOrder);
					mahjongMenu.setDealerSeat(dealerSeat);
					mahjongMenu.setMyCallPending(myCallPending);
					mahjongMenu.setWinningTile(winningTile);
					mahjongMenu.setCallOptions(canRiichi,canPon,canDaiminkan,chiOptions,ankanOptions,kakanOptions);
					mahjongMenu.setWinOptions(canTsumo, canRon);
					mahjongMenu.setAbortiveDraw(canDeclareKyuushu);
					mahjongMenu.setDrawReason(drawReason);
					mahjongMenu.updateScores(scores,settleScores);

					String label = "doraIndicator : ";
					for(Tile t : newDoraIndicator) label += t.getTextureId() + " ";
					RiichiMahjong.LOGGER.info(label);
				}
			});
		}));

		ClientPlayNetworking.registerGlobalReceiver(ModPackets.WIN_GAME_UPDATE_PACKET,((client, handler, buf, responseSender) -> {
			int lastWinSeat = buf.readInt();

			if(lastWinSeat < 0) return;

			int winnerHandSize = buf.readInt();
			List<Tile> winnerHand = new ArrayList<>();
			for(int i=0;i<winnerHandSize;i++){
				winnerHand.add(Tile.readFromBuf(buf));
			}

			int winnerMeldSize = buf.readInt();
			List<List<Tile>> newWinnersMelds = new ArrayList<>();
			List<MeldType> newWinnersMeldTypes = new ArrayList<>();
			for(int i=0;i<winnerMeldSize;i++){
				MeldType type = buf.readEnum(MeldType.class);
				int tileCount = buf.readInt();
				List<Tile> meld = new ArrayList<>();
				for(int j=0;j<tileCount;j++){
					meld.add(Tile.readFromBuf(buf));
				}
				newWinnersMelds.add(meld);
				newWinnersMeldTypes.add(type);
			}

			int uraDoraCount = buf.readInt();
			List<Tile> newUraDoraIndicator = new ArrayList<>();
			for(int i=0;i<uraDoraCount;i++){
				newUraDoraIndicator.add(Tile.readFromBuf(buf));
			}

			int lastWinHan = buf.readInt();
			int lastWinFu = buf.readInt();
			int lastWinPoints = buf.readInt();
			String lastWinLabel = buf.readUtf();

			int yakuSize = buf.readInt();
			List<String> yakuList = new ArrayList<>();
			List<Integer> yakuHanList = new ArrayList<>();
			for(int i=0;i<yakuSize;i++){
				yakuList.add(buf.readUtf());
				yakuHanList.add(buf.readInt());
			}

			client.execute(() -> {
				if(client.player.containerMenu instanceof MahjongMenu mahjongMenu) {
					mahjongMenu.updateWinnerTiles(winnerHand, newWinnersMelds, newWinnersMeldTypes);
					mahjongMenu.updateUraDora(newUraDoraIndicator);
					mahjongMenu.setLastWins(lastWinSeat, lastWinHan, lastWinFu, lastWinPoints, lastWinLabel);
					mahjongMenu.setYakuAndHan(yakuList, yakuHanList);

					String ulabel = "uraDoraIndicator : ";
					for (Tile t : newUraDoraIndicator) ulabel += t.getTextureId() + " ";
					RiichiMahjong.LOGGER.info(ulabel);
				}
				// MahjongTableBlock에서 보낸 패킷이 작동하지 않음
//				}else{
//					RiichiMahjong.LOGGER.info("not MahjongMenu but why");	// 패킷은 잘 전달되고 execute까지 도달하지만 MahjongMenu가 아니라고 판단.
//				}
			});
		}));

		ClientPlayNetworking.registerGlobalReceiver(ModPackets.UPDATE_HAND_MAKE_UP,((client, handler, buf, responseSender) -> {
			boolean handMakeUp = buf.readBoolean();

			client.execute(()->{
				if(client.player.containerMenu instanceof MahjongMenu mahjongMenu){
					mahjongMenu.setHandMakeUp(handMakeUp);
				}
			});
		}));

		ClientPlayNetworking.registerGlobalReceiver(ModPackets.LOBBY_SEAT_PACKET,((client, handler, buf, responseSender) -> {
			int mySeat = buf.readInt();
			UUID[] uuids = new UUID[4];
			for(int i=0;i<4;i++){
				uuids[i] = buf.readBoolean() ? buf.readUUID() : null;
			}
			client.execute(() -> {
				if(client.player.containerMenu instanceof MahjongMenu menu){
					menu.setLobby(mySeat,uuids);
				}
			});
		}));
	}
}