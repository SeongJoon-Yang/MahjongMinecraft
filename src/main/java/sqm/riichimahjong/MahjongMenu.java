package sqm.riichimahjong;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MahjongMenu extends AbstractContainerMenu {
    private MahjongGame.GameState state;
    private int mySeat = -1;
    private final UUID[] seatUuids = new UUID[4];

    private final List<Tile> playerHand;
    private final List<Tile> discardedTiles;
    private List<List<Tile>> allDiscarded = new ArrayList<>();
    private final List<List<Tile>> melds = new ArrayList<>();
    private final List<MeldType> meldTypes = new ArrayList<>();
    private final List<Tile> doraIndicatorPool = new ArrayList<>();
    private final List<Tile> uraDoraIndicatorPool = new ArrayList<>();
    private int[] scoreBySeat = new int[]{25000, 25000, 25000, 25000};
    private int[] settleScoreBySeat = new int[]{0, 0, 0, 0};
    private boolean[] isRiichi = new boolean[4];

    private final BlockPos tablePos;
    private boolean handMakeUp = true;
    private boolean gameEnd = false;
    private boolean matchOver = false;
    private boolean win = false;
    private boolean isTsumo = false;
    private int turnOrder = 0;
    private int dealerSeat = 0;
    private boolean myCallPending = false;
    private int wallSize = 69;
    private int roundWind = 0;
    private boolean isFuriten = false;
    private List<String> yaku;
    private List<Integer> han;
    private Tile winningTile = null;
    private int lastWinSeat = -1;
    private int lastWinHan = 0;
    private int lastWinFu = 0;
    private int lastWinScores = 0;
    private String lastWinLabel = "";

    private boolean canRiichi = false;
    private boolean canPon = false;
    private boolean canDaiminkan = false;
    private List<List<Integer>> chiOptions = new ArrayList<>();
    private List<Tile> ankanOptions = new ArrayList<>();
    private List<Tile> kakanOptions = new ArrayList<>();
    private boolean canTsumo = false;
    private boolean canRon = false;
    private boolean canDeclareKyuushu = false;
    private String drawReason;

    // 서버용 생성자: 이미 뽑아둔 손패를 직접 받음
    public MahjongMenu(int syncId, List<Tile> playerHand, List<Tile> discardedTiles, BlockPos tablePos) {
        super(ModMenuTypes.MAHJONG_MENU, syncId);
        this.playerHand = playerHand;
        this.discardedTiles = discardedTiles;
        this.tablePos = tablePos;
        this.gameEnd = false;
        this.matchOver = false;
        this.win = false;
        this.isTsumo = false;
        this.turnOrder = 0;
        this.dealerSeat = 0;
        this.yaku = new ArrayList<>();
        this.winningTile = null;
    }

    // 클라이언트용 생성자: 네트워크로 온 버퍼에서 손패를 읽어서 복원
    public MahjongMenu(int syncId, Inventory inventory, FriendlyByteBuf buf) {
        super(ModMenuTypes.MAHJONG_MENU, syncId);

        this.state = buf.readEnum(MahjongGame.GameState.class);

        int size = buf.readInt();
        List<Tile> hand = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            hand.add(Tile.readFromBuf(buf));
        }
        this.playerHand = hand;

        int discardSize = buf.readInt();
        List<Tile> discarded = new ArrayList<>();
        for(int i = 0; i < discardSize; i++){
            discarded.add(Tile.readFromBuf(buf));
        }
        this.discardedTiles = discarded;

        // 디버그용 나중에 수정될 수 있음
        int seats = buf.readInt();
        for(int i=0;i<seats;i++){
            List<Tile> discardedSeat = new ArrayList<>();
            int discardSeatSize = buf.readInt();
            for(int j=0;j<discardSeatSize;j++){
                discardedSeat.add(Tile.readFromBuf(buf));
            }
            allDiscarded.add(discardedSeat);
        }

        int meldCount = buf.readInt();
        for(int i=0;i<meldCount;i++){
            MeldType type = buf.readEnum(MeldType.class);
            int tileCount = buf.readInt();
            List<Tile> meld = new ArrayList<>();
            for(int j=0;j<tileCount;j++){
                meld.add(Tile.readFromBuf(buf));
            }
            melds.add(meld);
            meldTypes.add(type);
        }

        for(int i=0;i<4;i++){
            this.isRiichi[i] = buf.readBoolean();
        }

        this.wallSize = buf.readInt();
        this.roundWind = buf.readInt();
        this.isFuriten = buf.readBoolean();

        int doraCount = buf.readInt();
        for(int i=0;i<doraCount;i++){
            this.doraIndicatorPool.add(Tile.readFromBuf(buf));
        }
        int uraDoraCount = buf.readInt();
        for(int i=0;i<uraDoraCount;i++){
            this.uraDoraIndicatorPool.add(Tile.readFromBuf(buf));
        }

        this.tablePos = buf.readBlockPos();
        this.handMakeUp = buf.readBoolean();
        this.gameEnd = buf.readBoolean();
        this.matchOver = buf.readBoolean();
        this.win = buf.readBoolean();
        this.isTsumo = buf.readBoolean();
        this.turnOrder = buf.readInt();
        this.dealerSeat = buf.readInt();
        this.myCallPending = buf.readBoolean();

        this.lastWinSeat = buf.readInt();
        this.lastWinHan = buf.readInt();
        this.lastWinFu = buf.readInt();
        this.lastWinScores = buf.readInt();
        this.lastWinLabel = buf.readUtf();

        boolean hasWinningTile = buf.readBoolean();
        this.winningTile = hasWinningTile ? Tile.readFromBuf(buf) : null;

        int yakuSize = buf.readInt();
        List<Tile> ignore = null;   // 실수 방지용 메모라는데 뭔지 모르겠다. 실제로는 List<String> 사용하라고 하네
        List<String> yakuList = new ArrayList<>();
        List<Integer> yakuHanList = new ArrayList<>();
        for(int i=0;i<yakuSize;i++){
            yakuList.add(buf.readUtf());
            yakuHanList.add(buf.readInt());
        }
        this.yaku = yakuList;
        this.han = yakuHanList;

        this.canRiichi = buf.readBoolean();
        this.canPon = buf.readBoolean();
        this.canDaiminkan = buf.readBoolean();

        int chiCount = buf.readInt();
        for(int i=0;i<chiCount;i++){
            int idx1 = buf.readInt();
            int idx2 = buf.readInt();
            chiOptions.add(List.of(idx1,idx2));
        }

        int ankanCount = buf.readInt();
        for(int i=0;i<ankanCount;i++) ankanOptions.add(Tile.readFromBuf(buf));

        int kakanCount = buf.readInt();
        for(int i=0;i<kakanCount;i++) kakanOptions.add(Tile.readFromBuf(buf));

        this.canTsumo = buf.readBoolean();
        this.canRon = buf.readBoolean();
        this.canDeclareKyuushu = buf.readBoolean();

        boolean isDraw = buf.readBoolean();
        this.drawReason = isDraw ? buf.readUtf() : null;

        for(int i=0;i<4;i++){
            this.scoreBySeat[i] = buf.readInt();
        }

        for(int i=0;i<4;i++){
            this.settleScoreBySeat[i] = buf.readInt();
        }
    }

    public MahjongGame.GameState getGameState() { return state; }

    public List<Tile> getPlayerHand(){
        return playerHand;
    }

    public List<Tile> getDiscardedTiles(){
        return discardedTiles;
    }

    public BlockPos getTablePos(){
        return tablePos;
    }

    public List<String> getYaku() { return yaku; }

    public boolean isHandMakeUp() { return handMakeUp; }

    public boolean isGameEnd() { return gameEnd; }

    public boolean isMatchOver() { return matchOver; }

    public boolean isWin()  { return win; }

    public boolean isTsumo() { return isTsumo; }

    public int getTurnOrder() { return turnOrder; }

    public int getDealerSeat() { return dealerSeat; }

    public int getWallSize() { return wallSize; }

    public int getRoundWind() { return roundWind; }

    public List<List<Tile>> getMelds(){ return melds; }

    public List<MeldType> getMeldTypes(){ return meldTypes; }

    public List<Tile> getDoraIndicatorPool(){ return doraIndicatorPool; }

    public List<Tile> getUraDoraIndicatorPool(){ return uraDoraIndicatorPool; }

    public boolean canRiichi(){ return canRiichi; }

    public boolean canPon(){ return canPon; }

    public boolean canDaiminkan(){ return canDaiminkan; }

    public boolean canChi(){ return !chiOptions.isEmpty(); }

    public boolean canAnkan(){ return !ankanOptions.isEmpty(); }

    public boolean canKakan(){ return !kakanOptions.isEmpty(); }

    public boolean isFuriten(){ return isFuriten; }

    public List<List<Integer>> getChiOptions(){ return chiOptions; }

    public List<Tile> getAnkanOptions(){ return ankanOptions; }

    public List<Tile> getKakanOptions(){ return kakanOptions; }

    public List<List<Tile>> getAllDiscarded(){ return allDiscarded; }

    public int[] getScoreBySeat(){ return scoreBySeat; }

    public int[] getSettleScoreBySeat() { return settleScoreBySeat; }

    public boolean canTsumo(){ return canTsumo; }

    public boolean canRon(){ return canRon; }

    public boolean canDeclareKyuushu(){ return canDeclareKyuushu; }

    public String getDrawReason(){ return drawReason; }

    public boolean[] isRiichi() { return isRiichi; }

    public Tile getWinningTile() { return winningTile; }

    public List<Integer> getHan() { return han; }

    public int getWinSeat() { return lastWinSeat; }

    public int getWinHan() { return lastWinHan; }

    public int getWinFu() { return lastWinFu; }

    public int getWinScores() { return lastWinScores; }

    public String getWinLabel() { return lastWinLabel; }

    public int getMySeat() { return mySeat; }

    public int getViewSeat() { return Math.max(mySeat, 0); }

    public UUID getSeatUuid(int seat) { return seatUuids[seat]; }

    public boolean getMyCallPending(){ return myCallPending; }

    public void setGameState(MahjongGame.GameState state) { this.state = state;}

    public void setHandMakeUp(boolean handMakeUp)   { this.handMakeUp = handMakeUp; }

    public void setGameEnd(boolean gameEnd){
        this.gameEnd = gameEnd;
    }

    public void setMatchOver(boolean matchOver) { this.matchOver = matchOver; }

    public void setWin(boolean win) { this.win = win; }

    public void setTsumo(boolean isTsumo) { this.isTsumo = isTsumo; }

    public void setTurnOrder(int turnOrder) { this.turnOrder = turnOrder; }

    public void setDealerSeat(int dealerSeat) { this.dealerSeat = dealerSeat; }

    public void setMyCallPending(boolean myCallPending) { this.myCallPending = myCallPending; }

    public void setYakuAndHan(List<String> yaku, List<Integer> han) {
        this.yaku = yaku;
        this.han = han;
    }

    public void setAllDiscarded(List<List<Tile>> allDiscarded){ this.allDiscarded = allDiscarded; }

    public void updateHand(List<Tile> newHand){
        playerHand.clear();
        playerHand.addAll(newHand);
    }

    public void updateDiscarded(List<Tile> newDiscarded){
        discardedTiles.clear();
        discardedTiles.addAll(newDiscarded);
    }

    public void updateMelds(List<List<Tile>> newMelds, List<MeldType> newTypes){
        melds.clear();
        melds.addAll(newMelds);
        meldTypes.clear();
        meldTypes.addAll(newTypes);
    }

    public void updateWallSize(int wallSize){
        this.wallSize = wallSize;
    }

    public void updateRoundWind(int roundWind){
        this.roundWind = roundWind;
    }

    public void updateDora(List<Tile> newDora){
        doraIndicatorPool.clear();
        doraIndicatorPool.addAll(newDora);
    }

    public void updateUraDora(List<Tile> newDora){
        uraDoraIndicatorPool.clear();
        uraDoraIndicatorPool.addAll(newDora);
    }

    public void updateScores(int[] newScores, int[] settleScores){
        this.scoreBySeat = newScores;
        this.settleScoreBySeat = settleScores;
    }

    public void updateRiichi(boolean[] isRiichi) { this.isRiichi = isRiichi; }

    public void setCallOptions(boolean canRiichi, boolean canPon, boolean canDaiminkan,
                               List<List<Integer>> chiOptions,
                               List<Tile> ankanOptions, List<Tile> kakanOptions){
        this.canRiichi = canRiichi;
        this.canPon = canPon;
        this.canDaiminkan = canDaiminkan;
        this.chiOptions = chiOptions;
        this.ankanOptions = ankanOptions;
        this.kakanOptions = kakanOptions;
    }

    public void setWinOptions(boolean canTsumo, boolean canRon){
        this.canTsumo = canTsumo;
        this.canRon = canRon;
    }

    public void setLastWins(int lastWinSeat, int lastWinHan, int lastWinFu, int lastWinScores, String lastWinLabel){
        this.lastWinSeat = lastWinSeat;
        this.lastWinHan = lastWinHan;
        this.lastWinFu = lastWinFu;
        this.lastWinScores = lastWinScores;
        this.lastWinLabel = lastWinLabel;
    }
    public void setWinningTile(Tile winningTile) { this.winningTile = winningTile; }

    public void setAbortiveDraw(boolean canDeclareKyuushu){
        this.canDeclareKyuushu = canDeclareKyuushu;
    }

    public void setDrawReason(String drawReason){
        this.drawReason = drawReason;
    }

    public void setFuriten(boolean isFuriten){
        this.isFuriten = isFuriten;
    }

    public void setLobby(int mySeat, UUID[] uuids){
        this.mySeat = mySeat;
        System.arraycopy(uuids, 0, seatUuids, 0, 4);
    }

    // 나중에 거리 추가함 이라고 ai가 말했어
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // 필수라서 빈 코드로 만들어 놓음 나중에 어떻게 바꾸지?
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player){
        super.removed(player);
        if(player.level().isClientSide) return;

        ServerLevel level = (ServerLevel) player.level();
        MahjongSavedData data = MahjongSavedData.get(level);
        MahjongGame game = data.getGame(tablePos);
        if(game != null && game.releaseSeat(player.getUUID())){   // 대기 상태에서만 true
            data.setDirty();
            LobbySync.broadcast(level,tablePos,game);
        }
    }
}