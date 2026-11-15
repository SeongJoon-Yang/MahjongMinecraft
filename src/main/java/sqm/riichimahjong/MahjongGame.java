package sqm.riichimahjong;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.*;

public class MahjongGame {
    private final List<Tile>[] hands = new List[4];
    private final List<Tile>[] discardedBySeat = new List[4];
    private final List<Tile>[] discardHistoryBySeat = new List[4];  // 후리텐 체크용
    private final List<List<Tile>>[] meldsBySeat = new List[4];
    private final List<MeldType>[] meldTypesBySeat = new List[4];
    private int[] scoreBySeat = new int[4];
    private int[] settleScoreBySeat = new int[4];
    private boolean[] isMenzenBySeat = {true, true, true, true};
    private boolean[] isRiichiBySeat = new boolean[4];
    private boolean[] tempFuritenBySeat = new boolean[4];
    private boolean[] riichiFuritenBySeat = new boolean[4];
    private boolean[] ippatsuValidBySeat = new boolean[4];
    private final List<Tile>[] kuikaeForbiddenBySeat = new List[4];
    private final Map<UUID, Integer> seatAssignment = new HashMap<>();

    private final List<Tile> deadWall;
    private final List<Tile> wall;
    private List<Tile> hand;
    private List<Tile> discarded;
    private List<Tile> discardHistory;
    private List<List<Tile>> melds;
    private List<MeldType> meldTypes;
    private List<List<Tile>> winningGroups;

    private final List<Tile> doraIndicatorPool = new ArrayList<>();
    private final List<Tile> uraDoraIndicatorPool = new ArrayList<>();
    private int revealedDoraCount = 1;

    private int[] seatWind = new int[4];
    private int roundWind = 0;

    public enum GameState { WAITING, PLAYING }
    private GameState state = GameState.WAITING;

    private boolean handMakeUp = true;
    private boolean gameEnd = false;
    private boolean win = false;    // true는 화료 false는 유국
    private boolean dealerRepeats = false;
    private boolean matchOver = false;

    private Tile winningTile = null;
    private Tile drawnTile = null;
    private Tile lastDiscardedTile = null;

    private int lastDiscardOwner = -1;  // 방금 버린 사람 순서
    private int lastWinSeat = -1;
    private int lastWinHan = 0;
    private int lastWinFu = 0;
    private int lastWinScores = 0;
    private String lastWinLabel = "";

    private int turnOrder = 0;
    private int dealerSeat = 0;
    private int honbaCount = 0;
    private int roundCount = 1;
    private int riichiStick = 0;
    private int canCallCount = 0;
    private int declareCallCount = 0;

    private int[] discardCount = new int[4];
    // 사깡유국 구현하려면 kanCount가 4일 때 각 사람마다 깡 친 횟수 확인해야 함
    private int kanCount = 0;
    private final int[] kanCountBySeat = new int[4];

    private boolean anyCallMadeThisRound = false;
    private boolean selfCallPassed = false;
    private boolean justDrew = false;

    private boolean isRiichi = false;
    private boolean isTsumo = false;
    private boolean ippatsuValid = false;
    private boolean[] isDoubleRiichi = new boolean[4];
    private boolean isRinshan = false;  // 영상개화
    private boolean isChankan = false;  // 창깡(구조만 추가, 4인용 확장)
    private String drawReason = null;

    private final CallChoice[] callChoices = {CallChoice.NONE, CallChoice.NONE, CallChoice.NONE, CallChoice.NONE};
    private final int[] chiChoiceIndex = {-1, -1, -1, -1};

    private static final int MAX_KAN = 4;   // 기본값 4, 테스트 시에만 줄이기

    private static final Set<String> DOUBLE_YAKUMAN = Set.of(
        "국사무쌍13면대기", "순정구련보등", "대사희"
    );

    private static final Set<String> YAKUMAN_NAMES = Set.of(
            "천화", "지화", "국사무쌍", "국사무쌍13면대기", "구련보등", "순정구련보등", "녹일색",
            "자일색", "청노두", "대삼원", "대사희", "소사희", "쓰안커", "쓰깡즈"
    );

    private static final Map<String,Integer> HAN_CLOSED = Map.ofEntries(
            Map.entry("리치", 1), Map.entry("더블리치", 2), Map.entry("일발", 1),
            Map.entry("멘젠쯔모", 1), Map.entry("탕야오", 1), Map.entry("핑후", 1),
            Map.entry("이페코", 1), Map.entry("량페코", 3), Map.entry("치또이", 2),
            Map.entry("또이또이", 2), Map.entry("산안커", 2), Map.entry("삼색동순", 2),
            Map.entry("삼색동각", 2), Map.entry("일기통관", 2), Map.entry("찬타", 2),
            Map.entry("준찬타", 3), Map.entry("혼노두", 2), Map.entry("혼일색", 3),
            Map.entry("청일색", 6), Map.entry("영상개화", 1), Map.entry("창강", 1),
            Map.entry("해저로월", 1), Map.entry("하저로어", 1), Map.entry("산깡즈", 2),
            Map.entry("역패:백", 1), Map.entry("역패:발", 1), Map.entry("역패:중", 1),
            Map.entry("역패:장풍패",1), Map.entry("역패:자풍패",1)
    );

    private static final Map<String,Integer> HAN_OPEN = Map.ofEntries(
            Map.entry("탕야오", 1), Map.entry("또이또이", 2), Map.entry("산안커", 2),
            Map.entry("삼색동순", 1), Map.entry("삼색동각", 2), Map.entry("일기통관", 1),
            Map.entry("찬타", 1), Map.entry("준찬타", 2), Map.entry("혼노두", 2),
            Map.entry("혼일색", 2), Map.entry("청일색", 5), Map.entry("영상개화", 1),
            Map.entry("창강", 1), Map.entry("해저로월", 1), Map.entry("하저로어", 1),
            Map.entry("산깡즈", 2), Map.entry("역패:백", 1), Map.entry("역패:발", 1),
            Map.entry("역패:중", 1), Map.entry("역패:장풍패",1), Map.entry("역패:자풍패",1)
    );

    // 5 67 8910 1112 13

    private static final Set<String> KYUUSHU_TILES = new HashSet<>();
    static{
        for(String suit : new String[]{"man","sou","pin"}){
            KYUUSHU_TILES.add(suit+"_1");
            KYUUSHU_TILES.add(suit+"_9");
        }
        for(int i=1;i<=7;i++){
            KYUUSHU_TILES.add("honor_"+i);
        }
    }

    public MahjongGame(){
        List<Tile> fullDeck = TileDeck.shuffledDeck();

        this.deadWall = new ArrayList<>(fullDeck.subList(122,136));
        this.wall = new ArrayList<>(fullDeck.subList(0,122));
        this.hand = new ArrayList<>();
        this.discarded = new ArrayList<>();
        this.discardHistory = new ArrayList<>();
        this.melds = new ArrayList<>();
        this.meldTypes = new ArrayList<>();

        initDoraIndicators();

        for(int i=0;i<4;i++){
            hands[i] = new ArrayList<>();
            discardedBySeat[i] = new ArrayList<>();
            discardHistoryBySeat[i] = new ArrayList<>();
            meldsBySeat[i] = new ArrayList<>();
            meldTypesBySeat[i] = new ArrayList<>();
            kuikaeForbiddenBySeat[i] = new ArrayList<>();
            scoreBySeat[i] = 25000;
            settleScoreBySeat[i] = 0;
        }

        hands[0] = hand;
        discardedBySeat[0] = discarded;
        discardHistoryBySeat[0] = discardHistory;
        meldsBySeat[0] = melds;
        meldTypesBySeat[0] = meldTypes;
    }

    // 불러오기용 생성자
    public MahjongGame(boolean empty){
        this.wall = new ArrayList<>();
        this.deadWall = new ArrayList<>();
        this.hand = new ArrayList<>();
        this.discarded = new ArrayList<>();
        this.discardHistory = new ArrayList<>();
        this.melds = new ArrayList<>();
        this.meldTypes = new ArrayList<>();

        for(int i=0;i<4;i++){
            hands[i] = new ArrayList<>();
            discardedBySeat[i] = new ArrayList<>();
            discardHistoryBySeat[i] = new ArrayList<>();
            meldsBySeat[i] = new ArrayList<>();
            meldTypesBySeat[i] = new ArrayList<>();
            kuikaeForbiddenBySeat[i] = new ArrayList<>();
        }

        hands[0] = hand;
        discardedBySeat[0] = discarded;
        discardHistoryBySeat[0] = discardHistory;
        meldsBySeat[0] = melds;
        meldTypesBySeat[0] = meldTypes;
    }

    public Tile drawTile(int seat){
        // if(canCallCount != 0) return null;   // 콜이 가능하면 콜 먼저 처리하기 위해 뽑지 않는다.

        switchToSeat(seat);

        if(gameEnd){
            return null;
        }

        if(wall.isEmpty()){
            gameEnd = true;
            win = false;
            dealerRepeats = isTenpai(new ArrayList<>(hands[dealerSeat]), dealerSeat);
            boolean nagashi = false;
            for(int i=0;i<4;i++){
                if(isNagashiMangan(i)) nagashi = true;
            }
            drawReason = nagashi ? "유국만관" : "황패";
            if(!nagashi) settleDrawScores();
            settleNagashiMangan();
            checkMatchOver();
            return null;
        }

        selfCallPassed = false;
        justDrew = true;

        Tile drawn = wall.remove(0);
        hand.add(drawn);
        drawnTile = drawn;
        winningTile = drawn;

        tempFuritenBySeat[seat] = false;

        RiichiMahjong.LOGGER.info("drawTile : {}, isRed : {}",drawn.getDisplayName(),drawn.isRed());

        return drawn;
    }

    public Tile discardTileAt(int seat, int index){
        switchToSeat(seat);

        int expectedSize = 14 - 3 * melds.size();
        if(index >= 0 && index < hand.size() && kuikaeForbiddenBySeat[seat].contains(hand.get(index))){
            RiichiMahjong.LOGGER.info("discardTileAt 실패 : 쿠이카에로 막힌 패");
            return null;
        }
        if(gameEnd || index < 0 || index >= hand.size() || hand.size() != expectedSize){
            RiichiMahjong.LOGGER.info("discardTileAt 실패: 사이즈 불일치 또는 게임종료");
            return null;
        }

        if(turnOrder != seat || isRiichiBySeat[seat]){
            if(index != expectedSize - 1) return null;
            if(!hand.get(index).equals(drawnTile)) return null;
        }

        justDrew = false;

        Tile tile = hand.remove(index);
        discarded.add(tile);
        discardHistory.add(tile);
        lastDiscardedTile = tile;

        if(ippatsuValidBySeat[seat]){
            if(turnOrder == seat){
                ippatsuValidBySeat[seat] = false;
            }
        }

        lastDiscardOwner = turnOrder;
        isRinshan = false;
        discardCount[turnOrder]++;
        turnOrder = ++turnOrder % 4;

        if(handMakeUp){
            handSort(hand);
        }

        kuikaeForbiddenBySeat[seat].clear();

        return tile;
    }

    public boolean declareRiichi(int seat, int discardIndex){
        switchToSeat(seat);

        if(!isMenzenBySeat[seat] || isRiichiBySeat[seat] || gameEnd || turnOrder != seat){
            return false;
        }
        int expectedSize = 14 - 3 * melds.size();
        if(hand.size() != expectedSize || discardIndex < 0 || discardIndex >= hand.size()){
            return false;
        }

        List<Tile> testHand = new ArrayList<>(hand);
        testHand.remove(discardIndex);
        if(!isTenpai(testHand, seat)) return false;

        Tile discardTile = hand.remove(discardIndex);
        discarded.add(discardTile);
        discardHistory.add(discardTile);
        lastDiscardedTile = discardTile;

        isDoubleRiichi[turnOrder] = (discardCount[turnOrder] == 0 && !anyCallMadeThisRound);
        lastDiscardOwner = turnOrder;
        discardCount[turnOrder]++;
        turnOrder = ++turnOrder % 4;
        isRinshan = false;
        isRiichiBySeat[seat] = true;
        ippatsuValidBySeat[seat] = true;
        scoreBySeat[seat] -= 1000;
        riichiStick++;

        if(handMakeUp){
            handSort(hand);
        }

        return true;
    }

    public boolean checkWinCondition(int seat) {
        switchToSeat(seat);

        int totalTiles = hand.size();
        for(List<Tile> meld : melds) totalTiles += meld.size();
        if(totalTiles != 14 + kanCount) return false;
        if (isKokushi(hand, seat)) return true;
        if (isChuurenPoutou(seat)) return true;
        if (isChiitoitsu(hand, seat)) return true;
        if (getWinningGroups(hand) == null) return false;
        return !getYaku(seat).isEmpty();
    }

    public List<String> getYaku(int seat){
        switchToSeat(seat);

        if(isChiitoitsu(hand, seat)){
            winningGroups = null;
            return evaluateForGroups(null,seat);
        }

        List<List<List<Tile>>> allDecompositions = getAllWinningGroups(hand);
        if(allDecompositions.isEmpty()){
            winningGroups = null;
            return evaluateForGroups(null,seat);    // 국사무쌍 같은 특수 역
        }

        List<String> best = null;
        int bestHan = -1;
        winningGroups = null;
        for(List<List<Tile>> groups : allDecompositions){
            List<String> candidate = evaluateForGroups(groups,seat);
            int han = sumHandForComparison(candidate, seat);
            if(han > bestHan){
                bestHan = han;
                best = candidate;
                winningGroups = groups;
            }
        }
        return best;
    }

    public List<String> evaluateForGroups(List<List<Tile>> concealedGroups, int seat) {
        switchToSeat(seat);

        boolean chiitoi = isChiitoitsu(hand, seat);
        int[] sangenpai = countSangenpai();
        int[] kazehai = countKazehai();
        List<List<Tile>> tileBundle = new ArrayList<>();
        if(concealedGroups != null) tileBundle.addAll(concealedGroups);
        for(int i=0;i<melds.size();i++){
            List<Tile> meld = melds.get(i);
            if(meldTypes.get(i) == MeldType.CHI || meldTypes.get(i) == MeldType.PON){
                tileBundle.add(meld);
            }else{
                // 깡은 4장이지만 모양 판정(순자/각자 체크)에서는 3장짜리 커쯔로 취급
                // 순자/각자 체크가 뭐임?
                tileBundle.add(meld.subList(0,3));
            }
        }

        List<String> yakuman = new ArrayList<>();

        if(isTenhou(seat)){
            yakuman.add("천화");
        }else if(isChihou(seat)){
            yakuman.add("지화");
        }
        if(isKokushi(hand,seat)) yakuman.add(isKokushiJusanmen(hand,seat)?"국사무쌍13면대기":"국사무쌍");
        if(isChuurenPoutou(seat)) yakuman.add(isJunseiChuurenPoutou(hand,seat)?"순정구련보등":"구련보등");
        if(isRyuiisou()) yakuman.add("녹일색");
        if(isTsuiisou()) yakuman.add("자일색");
        if(isChinroutou()) yakuman.add("청노두");
        if (isDaisangen(sangenpai)) yakuman.add("대삼원");
        if(isDaisushi(kazehai)){
            yakuman.add("대사희");
        }else if(isShosushi(kazehai)){
            yakuman.add("소사희");
        }
        if(isSuuankou(concealedGroups)) yakuman.add("쓰안커");
        if(kanCountBySeat[seat] == 4) yakuman.add("쓰깡즈");

        if(!yakuman.isEmpty()) return yakuman;

        List<String> yaku = new ArrayList<>();

        if(isRiichiBySeat[seat]){
            yaku.add(isDoubleRiichi[seat] ? "더블리치" : "리치");    // 지금은 플레이어가 나밖에 없어서 0으로 진행
            if(ippatsuValidBySeat[seat]) yaku.add("일발");
        }

        if (isMenzenBySeat[seat] && isTsumo) yaku.add("멘젠쯔모");

        if (chiitoi) yaku.add("치또이");   // 칠대자
        if (isToitoi()) yaku.add("또이또이");
        if (isTanyao()) yaku.add("탕야오");

        if (isPinfu(tileBundle, seat)) yaku.add("핑후");

        if (isChinitsu()){
            yaku.add("청일색");
        } else if (isHonitsu()){
            yaku.add("혼일색");
        }

        if (isBakaze()) yaku.add("역패:장풍패");
        if (isJikaze(seat)) yaku.add("역패:자풍패");

        if (isShousangen(sangenpai)) {
            yaku.add("소삼원");
        } else {
            if (isHaku(sangenpai[0])) yaku.add("역패:백");
            if (isHatsu(sangenpai[1])) yaku.add("역패:발");
            if (isChun(sangenpai[2])) yaku.add("역패:중");
        }

        if (!chiitoi){
            if(isIttsuu(tileBundle)){
                yaku.add("일기통관");
            }
            if(isSanshokuDoujun(tileBundle)){
                yaku.add("삼색동순");
            }
        }

        // 치또이 형태에서 준찬타 같이 붙음 근데 치또이 형태에서는 찬타/준찬타가 붙을 수 없다 혼노두는 붙을듯
        if(isHonroutou()){
            yaku.add("혼노두");
        }else if(!chiitoi) {
            if (isJunchan(tileBundle)) {
                yaku.add("준찬타");
            } else if (isChanta(tileBundle)) {
                yaku.add("찬타");
            }
        }

        if (isSanankou(concealedGroups)) yaku.add("산안커");

        if(isSanshokuDokou(tileBundle)) yaku.add("삼색동각");

        if(isMenzen(seat)) {
            if (isRyanpeiko(tileBundle)) {
                yaku.add("량페코");
            } else if (isIipeiko(tileBundle)) {
                yaku.add("이페코");
            }
        }

        if (isRinshan) yaku.add("영상개화");
        if (kanCountBySeat[seat] == 3) yaku.add("산깡즈");

        // 창강은 4인용에서 "내가 아닌 다른 플레이어의 가깡 패로 론"이 가능해져야 실제로 트리거됩니다.
        // 지금은 isChankan 필드와 훅만 만들어두고, 실제 조건 연결은 4인용 확장 때 하시면 됩니다.
        if (isChankan) yaku.add("창강");

        if(wall.isEmpty()){
            yaku.add(isTsumo ? "해저로월" : "하저로어");
        }

        return yaku;
    }

    public List<List<Tile>> getWinningGroups(List<Tile> targetHand){
        List<Tile> sorted = new ArrayList<>(targetHand);
        handSort(sorted);

        Set<String> triedPairs = new HashSet<>();
        for(int i=0; i<sorted.size()-1;i++){
            Tile t1 = sorted.get(i);
            Tile t2 = sorted.get(i+1);
            if(t1.equals(t2)){
                String key = t1.getTextureId();
                if(triedPairs.contains(key)) continue;
                triedPairs.add(key);

                List<Tile> remaining = new ArrayList<>(sorted);
                remaining.remove(i+1);
                remaining.remove(i);

                List<List<Tile>> result = canFormMelds(remaining);
                if(result != null){
                    result.add(List.of(t1,t2));
                    return result;
                }
            }
        }
        return null;
    }

    private List<List<Tile>> canFormMelds(List<Tile> tiles){
        if(tiles.isEmpty()){
            return new ArrayList<List<Tile>>();
        }

        Tile first = tiles.get(0);

        int sameCount = 0;
        for(Tile t : tiles){
            if(t.equals(first)){
                sameCount++;
            }
        }
        if(sameCount>=3){
            List<Tile> next = new ArrayList<Tile>(tiles);
            List<Tile> tileBundle = new ArrayList();
            tileBundle.add(removeOne(next, first));
            tileBundle.add(removeOne(next, first));
            tileBundle.add(removeOne(next, first));
            List<List<Tile>> subResult = canFormMelds(next);
            if(subResult != null){
                subResult.add(tileBundle);
                return subResult;
            }
        }

        if(first.getSuit() != TileSuit.HONOR){
            Tile second = new Tile(first.getSuit(), first.getNumber()+1);
            Tile third = new Tile(first.getSuit(), first.getNumber()+2);
            if(tiles.contains(second) && tiles.contains(third)){
                List<Tile> next = new ArrayList<Tile>(tiles);
                List<Tile> tileBundle = new ArrayList();
                tileBundle.add(removeOne(next, first));
                tileBundle.add(removeOne(next, second));
                tileBundle.add(removeOne(next, third));
                List<List<Tile>> subResult = canFormMelds(next);
                if(subResult != null){
                    subResult.add(tileBundle);
                    return subResult;
                }
            }
        }
        return null;
    }

    private boolean isTanyao(){
        for (Tile tile : getAllTiles()){
            if(tile.getSuit() == TileSuit.HONOR) return false;
            if(tile.getNumber() == 1 || tile.getNumber() == 9) return false;
        }
        return true;
    }

    private boolean isChinitsu(){
        return isSingleSuit(false);
    }

    private boolean isHonitsu() {
        return isSingleSuit(true);
    }

    private boolean isChiitoitsu(List<Tile> targetHand, int seat){
        if(!isMenzen(seat)) return false;

        Map<String, Integer> counts = new HashMap<>();
        for(Tile tile : targetHand){
            counts.merge(tile.getTextureId(), 1, Integer::sum);
        }
        if(counts.size()!=7){
            return false;
        }
        for(int count : counts.values()){
            if(count!=2){
                return false;
            }
        }
        return true;
    }

    private boolean isToitoi(){
        Map<String, Integer> counts = new HashMap<>();
        for(Tile tile : getAllTiles()){
            counts.merge(tile.getTextureId(), 1, Integer::sum);
        }
        if(counts.size()!=5){
            return false;
        }

        int tripletKinds = 0;
        int pairKinds = 0;
        for(int count : counts.values()){
            if(count>=3) tripletKinds++;
            else if(count==2) pairKinds++;
        }
        return tripletKinds == 4 && pairKinds == 1;
    }

    private boolean isDaisangen(int[] sangenpai){
        int tripletCount = 0;
        for(int sangen : sangenpai){
            if(sangen>=3) tripletCount++;
        }
        return tripletCount==3;
    }

    private boolean isShousangen(int[] sangenpai){
        int tripletCount = 0;
        for(int sangen : sangenpai){
            if(sangen>=3) tripletCount++;
        }
        return tripletCount==2 && (sangenpai[0]==2 || sangenpai[1]==2 || sangenpai[2]==2);
    }

    private boolean isHaku(int haku){ return haku>=3; }

    private boolean isHatsu(int hatsu){
        return hatsu>=3;
    }

    private boolean isChun(int chun){
        return chun>=3;
    }

    private boolean isIttsuu(List<List<Tile>> groups){
        for(TileSuit suit : new TileSuit[]{TileSuit.MAN, TileSuit.SOU, TileSuit.PIN}){
            boolean has123 = false, has456 = false, has789 = false;
            for(List<Tile> group : groups){
                if(group.size()!=3) continue;
                if (isSequence(group, suit, 1)) has123 = true;
                if (isSequence(group, suit, 4)) has456 = true;
                if (isSequence(group, suit, 7)) has789 = true;
            }
            if (has123 && has456 && has789) {
                return true;
            }
        }
        return false;
    }

    private boolean isSanshokuDoujun(List<List<Tile>> groups){
        for(int i=1;i<=9;i++){
            boolean hasMan = false, hasSou = false, hasPin = false;
            for(List<Tile> group : groups){
                if(group.size()!=3) continue;
                if(isSequence(group, TileSuit.MAN, i)) hasMan = true;
                if(isSequence(group, TileSuit.SOU, i)) hasSou = true;
                if(isSequence(group, TileSuit.PIN, i)) hasPin = true;
            }
            if(hasMan && hasSou && hasPin){
                return true;
            }
        }
        return false;
    }

    private boolean isSanshokuDokou(List<List<Tile>> groups){
        for(int i=1;i<=9;i++){
            boolean hasMan = false, hasSou = false, hasPin = false;
            for(List<Tile> group : groups){
                if(group.size()!=3) continue;
                if(isTriplet(group, TileSuit.MAN, i)) hasMan = true;
                if(isTriplet(group, TileSuit.SOU, i)) hasSou = true;
                if(isTriplet(group, TileSuit.PIN, i)) hasPin = true;
            }
            if(hasMan && hasSou && hasPin){
                return true;
            }
        }
        return false;
    }

    private boolean isIipeiko(List<List<Tile>> groups){
        return countSequencePairs(groups) == 1;
    }

    private boolean isRyanpeiko(List<List<Tile>> groups){
        return countSequencePairs(groups) == 2;
    }

    private boolean isHonroutou(){
        return hasOnlyRoutouhai(true);
    }

    private boolean isChinroutou(){
        return hasOnlyRoutouhai(false);
    }

    private boolean isTsuiisou(){
        for(Tile tile : getAllTiles()){
            if(tile.getSuit() != TileSuit.HONOR) return false;
        }
        return true;
    }

    private boolean isDaisushi(int[] kazehai){
        int tripletCount = 0;
        for(int kaze : kazehai){
            if(kaze>=3) tripletCount++;
        }
        return tripletCount==4;
    }

    private boolean isShosushi(int[] kazehai){
        int tripletCount = 0;
        for(int kaze : kazehai){
            if(kaze>=3) tripletCount++;
        }
        return tripletCount==3 && (kazehai[0]==2 || kazehai[1]==2 || kazehai[2]==2 || kazehai[3]==2);
    }

    private boolean isChanta(List<List<Tile>> tileBundle){
        return hasRoutouhai(tileBundle, true);
    }

    private boolean isJunchan(List<List<Tile>> tileBundle){
        return hasRoutouhai(tileBundle, false);
    }

    private boolean isSuuankou(List<List<Tile>> concealedGroups) {
        int tripletCount = countConcealedTriplet(concealedGroups);
        return tripletCount == 4;
    }

    private boolean isSanankou(List<List<Tile>> concealedGroups) {
        int tripletCount = countConcealedTriplet(concealedGroups);
        return tripletCount == 3;
    }

    private boolean isKokushi(List<Tile> targetHand, int seat){
        if(!isMenzen(seat)) return false;

        Set<String> requiredTypes = new HashSet<>();
        requiredTypes.add("man_1");
        requiredTypes.add("man_9");
        requiredTypes.add("sou_1");
        requiredTypes.add("sou_9");
        requiredTypes.add("pin_1");
        requiredTypes.add("pin_9");
        requiredTypes.add("honor_1");
        requiredTypes.add("honor_2");
        requiredTypes.add("honor_3");
        requiredTypes.add("honor_4");
        requiredTypes.add("honor_5");
        requiredTypes.add("honor_6");
        requiredTypes.add("honor_7");

        Map<String, Integer> counts = new HashMap<>();
        for(Tile tile : targetHand){
            String id = tile.getTextureId();
            if(!requiredTypes.contains(id)){
                return false;
            }
            counts.merge(id, 1, Integer::sum);
        }

        if(counts.size()!=13){
            return false;
        }

        int pairCount = 0;
        for(int count : counts.values()){
            if(count == 2) pairCount++;
        }
        return pairCount == 1;
    }

    private boolean isKokushiJusanmen(List<Tile> targetHand, int seat){
        if(!isKokushi(targetHand,seat)) return false;
        if(winningTile == null) return false;

        List<Tile> withoutWinningTile = new ArrayList<>(targetHand);
        withoutWinningTile.remove(winningTile);

        Set<String> types = new HashSet<>();
        for(Tile tile : withoutWinningTile){
            types.add(tile.getTextureId());
        }
        return types.size() == 13;
    }

    private boolean isChuurenPoutou(int seat){
        if(!isMenzen(seat)) return false;

        TileSuit suit = null;
        for(Tile tile : hand){
            if(tile.getSuit() == TileSuit.HONOR) return false;
            if(suit == null) {
                suit = tile.getSuit();
            }else if(suit != tile.getSuit()){
                return false;
            }
        }
        if(suit == null){
            return false;
        }

        int[] counts = new int[10]; // index 1~9 사용 (0 사용 안 함)
        for(Tile tile : hand){
            counts[tile.getNumber()]++;
        }

        if(counts[1] < 3 || counts[9] < 3) return false;

        for(int i=2;i<=8;i++){
            if(counts[i] < 1) return false;
        }

        return true;
    }

    private boolean isJunseiChuurenPoutou(List<Tile> targetHand, int seat){
        if(!isChuurenPoutou(seat)) return false;
        if(winningTile == null) return false;

        List<Tile> withoutWinningTile = new ArrayList<>(targetHand);
        withoutWinningTile.remove(winningTile);

        int[] counts = new int[10];
        for(Tile tile : withoutWinningTile){
            counts[tile.getNumber()]++;
        }

        if(counts[1] != 3 || counts[9] != 3) return false;
        for(int i=2; i<=8 ;i++){
            if(counts[i] != 1) return false;
        }
        return true;
    }

    private boolean isPinfu(List<List<Tile>> tileBundle, int seat){
        if(!isMenzen(seat)) return false;

        for(List<Tile> group : tileBundle){
            if(group.size() == 3){
                int tileNumber = 0;
                for(Tile t : group){
                    if(tileNumber == t.getNumber()) return false;
                    if(tileNumber == 0) tileNumber = t.getNumber();
                }
            }
            if(group.size() == 2){
                Tile t = group.get(0);      // 머리라서 같은패 2장이기 때문에 get()으로 가져옴
                if(t.getSuit() == TileSuit.HONOR){
                    int num = t.getNumber();
                    if(num == 5 || num == 6 || num == 7) return false;
                    if(num == seatWind[seat] + 1) return false;
                    if(num == roundWind + 1) return false;
                }
            }
        }

        for(List<Tile> group : tileBundle){
            if(group.contains(winningTile)){
                if(group.size() == 2) return false;

                List<Tile> tempGroup = new ArrayList<>(group);
                tempGroup.remove(winningTile);

                Tile firstTile = tempGroup.get(0);
                Tile secondTile = tempGroup.get(1);

                if(firstTile.getNumber()+1 == secondTile.getNumber()){
                    if(firstTile.getNumber() != 1 && secondTile.getNumber() != 9) return true;
                }
            }
        }
        return false;
    }

    private boolean isRyuiisou(){
        for(Tile tile : getAllTiles()){
            if(tile.getSuit() == TileSuit.SOU){
                int number = tile.getNumber();
                if(number != 2 && number != 3 && number != 4 && number != 6 && number != 8){
                    return false;
                }
            } else if(tile.getSuit() == TileSuit.HONOR){
                if(tile.getNumber() != 6) return false;
            } else return false;
        }
        return true;
    }

    private boolean isJikaze(int seat){
        int[] kazehai = countKazehai();
        int wind = seatWind[seat];
        return kazehai[wind]>=3;
    }

    private boolean isBakaze(){
        int[] kazehai = countKazehai();
        return kazehai[roundWind]>=3;
    }

    // 천화 지화 추가는 했는데 이렇게 진행해도 되는지 확인하기
    private boolean isTenhou(int seat){
        if(seat == dealerSeat && turnOrder == seat && discardCount[seat] == 0 && !anyCallMadeThisRound){
            return true;
        }
        return false;
    }

    private boolean isChihou(int seat){
        if(turnOrder == seat && discardCount[seat] == 0 && !anyCallMadeThisRound){
            return true;
        }
        return false;
    }

    private boolean isSingleSuit(boolean allowHonor){
        TileSuit foundSuit = null;
        boolean hasHonor = false;

        for (Tile tile : getAllTiles()){
            if(tile.getSuit() == TileSuit.HONOR){
                hasHonor = true;
                continue;
            }
            if(foundSuit == null){
                foundSuit = tile.getSuit();
            } else if(foundSuit != tile.getSuit()){
                return false;
            }
        }
        if(hasHonor && !allowHonor) return false;
        return foundSuit != null;
    }

    private boolean hasRoutouhai(List<List<Tile>> groups, boolean allowHonor){
        boolean hasHonor = false;
        for(List<Tile> group : groups){
            boolean routouhai = false;
            for(Tile tile : group){
                if(tile.getSuit() == TileSuit.HONOR){
                    hasHonor = true;
                    continue;
                }
                if(tile.getNumber() == 1 || tile.getNumber() == 9) routouhai = true;
            }
            if(!routouhai) return false;
        }
        if(hasHonor && !allowHonor) return false;
        return true;
    }

    private boolean hasOnlyRoutouhai(boolean allowHonor){
        boolean hasHonor = false;
        for(Tile tile : getAllTiles()){
            if(tile.getSuit() == TileSuit.HONOR){
                hasHonor = true;
                continue;
            }
            if(tile.getNumber() != 1 && tile.getNumber() != 9) return false;
        }
        if(hasHonor && !allowHonor) return false;
        return true;
    }

    private int[] countKazehai(){
        int[] kazehai = new int[4];

        for(Tile tile : getAllTiles()){
            if(tile.getSuit() == TileSuit.HONOR){
                switch(tile.getNumber()){
                    case 1 : kazehai[0]++; break;
                    case 2 : kazehai[1]++; break;
                    case 3 : kazehai[2]++; break;
                    case 4 : kazehai[3]++; break;
                }
            }
        }
        return kazehai;
    }

    private int[] countSangenpai(){
        int[] sangenpai = new int[3];

        for(Tile tile : getAllTiles()){
            if(tile.getSuit() == TileSuit.HONOR){
                switch(tile.getNumber()){
                    case 5 : sangenpai[0]++; break;
                    case 6 : sangenpai[1]++; break;
                    case 7 : sangenpai[2]++; break;
                }
            }
        }
        return sangenpai;
    }

    private List<Integer> extractSortedNumbers(List<Tile> group, TileSuit suit) {
        if (group.size() != 3) return null;
        List<Integer> numbers = new ArrayList<>();
        for (Tile t : group) {
            if (t.getSuit() != suit) return null;
            numbers.add(t.getNumber());
        }
        numbers.sort(null);
        return numbers;
    }

    private boolean isSequence(List<Tile> group, TileSuit suit, int startNumber) {
        List<Integer> numbers = extractSortedNumbers(group, suit);
        if(numbers == null) return false;
        return numbers.get(0) == startNumber
                && numbers.get(1) == startNumber + 1
                && numbers.get(2) == startNumber + 2;
    }

    private boolean isTriplet(List<Tile> group, TileSuit suit, int startNumber) {
        List<Integer> numbers = extractSortedNumbers(group, suit);
        if(numbers == null) return false;
        return numbers.get(0) == startNumber
                && numbers.get(1) == startNumber
                && numbers.get(2) == startNumber;
    }

    private int countConcealedTriplet(List<List<Tile>> concealedGroups){
        int count = 0;
        for(int i=0;i<melds.size();i++){
            if(meldTypes.get(i) == MeldType.ANKAN) count++;
        }
        if(concealedGroups == null) return count;

        for (List<Tile> group : concealedGroups) {
            if (group.size() != 3) continue; // 페어는 건너뜀
            Tile first = group.get(0);
            boolean allSame = true;
            for (Tile t : group) {
                if (!t.equals(first)) {
                    allSame = false;
                    break;
                }
            }
            if (!allSame) continue;
            if(!isTsumo && winningTile != null && first.equals(winningTile)) continue;
            count++;
        }
        return count;
    }

    private int countSequencePairs(List<List<Tile>> groups){
        int distinctPairCount = 0;
        for(TileSuit suit : new TileSuit[]{TileSuit.MAN, TileSuit.SOU, TileSuit.PIN}) {
            for (int i = 1; i <= 9; i++) {
                int sequenceCount = 0;
                for (List<Tile> group : groups) {
                    if (group.size() != 3) continue;
                    if (isSequence(group, suit, i)) sequenceCount++;
                }
                if (sequenceCount == 2) {
                    distinctPairCount++;
                }
            }
        }
        return distinctPairCount;
    }

    private Tile removeOne(List<Tile> tiles, Tile tile){
        Tile removedTile;
        for(int i=0;i<tiles.size();i++){
            if(tiles.get(i).equals(tile)){
                removedTile = tiles.get(i);
                tiles.remove(i);
                return removedTile;
            }
        }
        return null;
    }

    private boolean isTenpai(List<Tile> thirteenTiles, int seat){
        return !getWaitingTiles(thirteenTiles, seat).isEmpty();
    }

    public boolean canDeclareRiichi(int seat){
        switchToSeat(seat);

        if(isRiichiBySeat[seat] || gameEnd){
            RiichiMahjong.LOGGER.info("canDeclareRiichi fail: seat={}, isRiichiBySeat[seat]={}, gameEnd={}", seat, isRiichiBySeat[seat], gameEnd);
            return false;
        }
        if(turnOrder != seat){
            RiichiMahjong.LOGGER.info("canDeclareRiichi fail: turnOrder={}, seat={}", turnOrder, seat);
            return false;
        }
        if(!isMenzenBySeat[seat]){
            RiichiMahjong.LOGGER.info("canDeclareRiichi fail: not menzen");
            return false;
        }
        int expectedSize = 14 - 3 * melds.size();
        if(hand.size() != expectedSize){
            RiichiMahjong.LOGGER.info("canDeclareRiichi fail: hand.size()={}", hand.size());
            return false;
        }
        if(selfCallPassed){
            RiichiMahjong.LOGGER.info("canDeclareRiichi fail: self call passed");
            return false;
        }

        for(int i=0; i<hand.size(); i++){
            List<Tile> thirteenTiles = new ArrayList<>(hand);
            thirteenTiles.remove(i);
            if(isTenpai(thirteenTiles, seat)){
                return true;
            }
        }
        return false;
    }

    private List<Tile> getAllTiles(){
        List<Tile> all = new ArrayList<>(hand);
        for(List<Tile> meld : melds) all.addAll(meld);
        return all;
    }

    public boolean canPon(int seat){
        switchToSeat(seat);
        if(isRiichiBySeat[seat] || gameEnd) return false;
        if(lastDiscardedTile == null || lastDiscardOwner == -1 || lastDiscardOwner == seat){
            RiichiMahjong.LOGGER.info("canPon fail: lastDiscardedTile={}, lastDiscardOwner={}", lastDiscardedTile == null, lastDiscardOwner);
            return false;
        }
        int count = 0;
        for(Tile tile : hand) if(tile.equals(lastDiscardedTile)) count++;
        boolean result = count >= 2;
        RiichiMahjong.LOGGER.info("canPon: target={}, count={}, result={}", lastDiscardedTile, count, result);
        return result;
    }

    // 깡 4번 한 상태에서 깡 할 수 있는 패여도 깡 못하게 막기
    public boolean canDaiminkan(int seat){
        switchToSeat(seat);
        if(isRiichiBySeat[seat] || gameEnd) return false;
        if(kanCount >= MAX_KAN) return false;
        if(lastDiscardedTile == null || lastDiscardOwner == -1 || lastDiscardOwner == seat){
            RiichiMahjong.LOGGER.info("canDaiminkan fail: lastDiscardedTile={}, lastDiscardOwner={}", lastDiscardedTile == null, lastDiscardOwner);
            return false;
        }
        int count = 0;
        for(Tile tile : hand) if(tile.equals(lastDiscardedTile)) count++;
        boolean result = count >= 3;
        RiichiMahjong.LOGGER.info("canDaiminkan: target={}, count={}, result={}", lastDiscardOwner, count, result);
        return result;
    }

    public boolean canChi(int seat){
        switchToSeat(seat);
        if(isRiichiBySeat[seat] || gameEnd) return false;
        if(lastDiscardedTile == null || lastDiscardOwner == -1){
            RiichiMahjong.LOGGER.info("canChi fail: lastDiscardedTile={}, lastDiscardOwner={}", lastDiscardedTile == null, lastDiscardOwner);
            return false;
        }
        if((lastDiscardOwner + 1) % 4 != seat){
            RiichiMahjong.LOGGER.info("canChi fail: lastDiscardOwner={}", lastDiscardOwner);
            return false;
        }
        if(lastDiscardedTile.getSuit() == TileSuit.HONOR) return false;
        return !getChiOptions(lastDiscardedTile).isEmpty();
    }

    // 가능한 치 조합을 손패 인덱스 쌍으로 가져옴
    public List<List<Integer>> getChiOptions(Tile target){
        List<List<Integer>> options = new ArrayList<>();
        int number = target.getNumber();
        TileSuit suit = target.getSuit();
        int[][] patterns = {{-2,-1},{-1,1},{1,2}};
        for(int[] offsets : patterns){
            int num1 = number + offsets[0];
            int num2 = number + offsets[1];
            if(num1 < 1 || num1 > 9 || num2 < 1 || num2 > 9) continue;
            Integer idx1 = findTileIndex(new Tile(suit,num1));
            Integer idx2 = findTileIndex(new Tile(suit,num2));
            if(idx1 != null && idx2 != null && !idx1.equals(idx2)){
                options.add(List.of(idx1, idx2));
            }
        }
        return options;
    }

    public boolean canTsumo(int seat){
        switchToSeat(seat);

        if(selfCallPassed) return false;
        if(gameEnd || turnOrder != seat || !justDrew) return false;

        boolean prevTsumo = isTsumo;
        isTsumo = true;
        boolean result = checkWinCondition(seat);
        isTsumo = prevTsumo;
        return result;
    }

    public boolean canRon(int seat){
        switchToSeat(seat);
        if(gameEnd) return false;
        if(lastDiscardedTile == null || lastDiscardOwner == -1 || lastDiscardOwner == seat) return false;
        if(isFuriten(seat)) return false;

        boolean prevTsumo = isTsumo;
        isTsumo = false;
        hand.add(lastDiscardedTile);
        Tile prevWinningTile = winningTile;
        winningTile = lastDiscardedTile;
        boolean result = checkWinCondition(seat);
        hand.remove(hand.size()-1);
        winningTile = prevWinningTile;
        isTsumo = prevTsumo;
        return result;
    }

    public boolean declareTsumo(int seat){
        switchToSeat(seat);

        if(!canTsumo(seat)) return false;
        isTsumo = true;
        gameEnd = true;
        win = true;
        dealerRepeats = (seat == dealerSeat);
        settleScores(seat);
        return true;
    }

    public boolean declareRon(int seat){
        if(!canRon(seat)) return false;

        int discarderSeat = lastDiscardOwner;
        Tile target = lastDiscardedTile;

        switchToSeat(discarderSeat);
        discarded.remove(discarded.size()-1);

        switchToSeat(seat);
        hand.add(target);
        winningTile = target;
        isTsumo = false;
        gameEnd = true;
        win = true;
        dealerRepeats = (seat == dealerSeat);
        settleScores(seat);
        return true;
    }

    public Integer findTileIndex(Tile tile){
        for(int i=0;i<hand.size();i++){
            if(hand.get(i).equals(tile)) return i;
        }
        return null;
    }

    public boolean callPon(int seat){
        if(!canPon(seat)) return false;

        int discarderSeat = lastDiscardOwner;
        Tile target = lastDiscardedTile;

        switchToSeat(discarderSeat);
        discarded.remove(discarded.size()-1);

        switchToSeat(seat);
        List<Tile> meld = new ArrayList<>();
        meld.add(target);
        meld.add(removeOne(hand, target));
        meld.add(removeOne(hand, target));
        melds.add(meld);
        meldTypes.add(MeldType.PON);

        List<Tile> forbidden = new ArrayList<>();
        forbidden.add(new Tile(target.getSuit(),target.getNumber()));
        kuikaeForbiddenBySeat[seat] = forbidden;

        isMenzenBySeat[seat] = false;
        for(int i=0;i<4;i++) ippatsuValidBySeat[i] = false;
        justDrew = false;
        anyCallMadeThisRound = true;

        turnOrder = seat;
        return true;
    }

    public boolean callChi(int seat, int handIndex1, int handIndex2){
        if(!canChi(seat)) return false;
        Tile target = lastDiscardedTile;
        boolean valid = getChiOptions(target).stream()
                .anyMatch(opt -> opt.contains(handIndex1) && opt.contains(handIndex2));
        if(!valid) return false;

        int discarderSeat = lastDiscardOwner;
        switchToSeat(discarderSeat);
        discarded.remove(discarded.size()-1);

        switchToSeat(seat);
        List<Tile> meld = new ArrayList<>();
        meld.add(target);
        int lower = Math.min(handIndex1, handIndex2);
        int higher = Math.max(handIndex1, handIndex2);
        meld.add(hand.remove(higher));
        meld.add(hand.remove(lower));
        melds.add(meld);
        meldTypes.add(MeldType.CHI);

        List<Integer> nums = new ArrayList<>();
        for(Tile t : meld) nums.add(t.getNumber());
        Collections.sort(nums);
        int lo = nums.get(0), hi = nums.get(2);

        List<Tile> forbidden = new ArrayList<>();
        forbidden.add(new Tile(target.getSuit(),target.getNumber()));

        if(target.getNumber() == lo && hi + 1 <= 9){
            forbidden.add(new Tile(target.getSuit(), hi+1));
        }else if(target.getNumber() == hi && lo - 1 >= 1){
            forbidden.add(new Tile(target.getSuit(), lo-1));
        }

        kuikaeForbiddenBySeat[seat] = forbidden;

        isMenzenBySeat[seat] = false;
        for(int i=0;i<4;i++) ippatsuValidBySeat[i] = false;
        justDrew = false;
        anyCallMadeThisRound = true;

        turnOrder = seat;
        return true;
    }

    public boolean callDaiminkan(int seat){
        if(!canDaiminkan(seat)) return false;
        int discarderSeat = lastDiscardOwner;
        Tile target = lastDiscardedTile;

        switchToSeat(discarderSeat);
        discarded.remove(discarded.size()-1);

        switchToSeat(seat);
        List<Tile> meld = new ArrayList<>();
        meld.add(target);
        for(int i=0;i<3;i++) meld.add(removeOne(hand,target));
        melds.add(meld);
        meldTypes.add(MeldType.MINKAN);
        kanCount++;
        kanCountBySeat[seat]++;
        if(revealedDoraCount < doraIndicatorPool.size()){
            revealedDoraCount++;
        }

        isMenzenBySeat[seat] = false;
        for(int i=0;i<4;i++) ippatsuValidBySeat[i] = false;
        justDrew = false;
        anyCallMadeThisRound = true;

        turnOrder = seat;
        drawRinshanTile(seat);
        return true;
    }

    public boolean callAnkan(int seat, Tile tile){
        switchToSeat(seat);
        if(kanCount >= MAX_KAN) return false;

        int count = 0;
        for(Tile t : hand) if(t.equals(tile)) count++;
        if(count < 4 || turnOrder != seat) return false;

        List<Tile> meld = new ArrayList<>();
        for(int i=0;i<4;i++) meld.add(removeOne(hand,tile));
        melds.add(meld);
        meldTypes.add(MeldType.ANKAN);
        kanCount++;
        kanCountBySeat[seat]++;
        if(revealedDoraCount < doraIndicatorPool.size()){
            revealedDoraCount++;
        }
        anyCallMadeThisRound = true;

        drawRinshanTile(seat);
        return true;
    }

    public boolean callKakan(int seat, Tile tile){
        switchToSeat(seat);

        if(!canKakan(seat)) return false;
        for(int i=0;i<melds.size();i++){
            if(meldTypes.get(i) == MeldType.PON && melds.get(i).get(0).equals(tile)){
                Tile added = removeOne(hand,tile);
                if(added == null) return false;
                melds.get(i).add(added);
                meldTypes.set(i,MeldType.KAKAN);
                kanCount++;
                kanCountBySeat[seat]++;
                if(revealedDoraCount < doraIndicatorPool.size()){
                    revealedDoraCount++;
                }
                anyCallMadeThisRound = true;
                drawRinshanTile(seat);
                return true;
            }
        }
        return false;
    }

    public void drawRinshanTile(int seat){
        switchToSeat(seat);

        if(deadWall.isEmpty()) return;

        selfCallPassed = false;
        justDrew = true;

        Tile drawn = deadWall.remove(0);
        if(!wall.isEmpty()){
            deadWall.add(wall.remove(wall.size()-1));   // 왕패 14장 유지
        }
        hand.add(drawn);
        drawnTile = drawn;
        winningTile = drawn;
        isRinshan = true;
        turnOrder = seat;
    }

    public List<List<Integer>> getChiOptionsForSeat(int seat){
        switchToSeat(seat);
        if(!canChi(seat)) return new ArrayList<>();
        return getChiOptions(lastDiscardedTile);
    }

    public List<Tile> getAnkanOptions(int seat){
        switchToSeat(seat);

        List<Tile> options = new ArrayList<>();
        if(gameEnd || kanCount >= MAX_KAN || selfCallPassed) return options;
        if(!justDrew || turnOrder != seat) return options;
        Map<String,Integer> counts = new HashMap<>();
        for(Tile tile : hand) counts.merge(tile.getTextureId(),1,Integer::sum);
        Set<String> seen = new HashSet<>();
        for(Tile tile : hand){
            if (counts.get(tile.getTextureId()) >= 4 && seen.add(tile.getTextureId())){
                options.add(tile);
            }
        }
        return options;
    }

    public List<Tile> getKakanOptions(int seat){
        switchToSeat(seat);

        List<Tile> options = new ArrayList<>();
        if(gameEnd || kanCount >= MAX_KAN || selfCallPassed) return options;
        if(!justDrew || turnOrder != seat) return options;
        for(int i=0;i<melds.size();i++){
            if(meldTypes.get(i) == MeldType.PON){
                Tile ponTile = melds.get(i).get(0);
                if(findTileIndex(ponTile) != null) options.add(ponTile);
            }
        }
        return options;
    }

    // 깡 4번 한 상태에서 깡 할 수 있는 패여도 깡 못하게 막기
    public boolean canAnkan(int seat){
        switchToSeat(seat);

        if(selfCallPassed) return false;
        return !getAnkanOptions(seat).isEmpty();
    }

    // 깡 4번 한 상태에서 깡 할 수 있는 패여도 깡 못하게 막기
    public boolean canKakan(int seat){
        switchToSeat(seat);

        if(selfCallPassed) return false;
        return !getKakanOptions(seat).isEmpty();
    }

    public boolean canDeclareKyuushu(int seat){
        switchToSeat(seat);

        if(gameEnd) return false;
        if(!justDrew || turnOrder != seat) return false;
        if(discardCount[seat] != 0 || anyCallMadeThisRound) return false;
        if(selfCallPassed) return false;

        Set<String> types = new HashSet<>();
        for(Tile t : hand){
            if(KYUUSHU_TILES.contains(t.getTextureId())){
                types.add(t.getTextureId());
            }
        }
        return types.size()>=9;
    }

    public void clearCallState(){
        lastDiscardOwner = -1;
        lastDiscardedTile = null;
    }

    public int calculateHan(int seat){
        switchToSeat(seat);

        List<String> yaku = getYaku(seat);
        if(yaku.isEmpty()) return 0;

        if(YAKUMAN_NAMES.contains(yaku.get(0))){
            int total = 0;
            for(String y : yaku){
                total += DOUBLE_YAKUMAN.contains(y) ? 26 : 13;
            }
            return total;
        }

        Map<String,Integer> table = isMenzenBySeat[seat] ? HAN_CLOSED : HAN_OPEN;
        int han = 0;
        for(String y : yaku){
            Integer h = table.get(y);
            if(h == null){
                RiichiMahjong.LOGGER.warn("calculateHan : 테이블에 없는 역 이름 '{}', 0판 처리",y);
                h = 0;
            }else{
                RiichiMahjong.LOGGER.info("calculateHan : 역 : {}, 판수 : {}",y,h);
            }
            han += h;
        }
        RiichiMahjong.LOGGER.info("calculateHan : 역 계산 - 현재 판수 : {}",han);

        List<Tile> allTiles = getAllTilesForSeat();
        han += countDora(allTiles, getRevealedDoraIndicators());
        RiichiMahjong.LOGGER.info("calculateHan : 도라 추가 - 현재 판수 : {}",han);
        han += countAkaDora(allTiles);
        RiichiMahjong.LOGGER.info("calculateHan : 적도라 추가 - 현재 판수 : {}",han);
        if(isRiichiBySeat[seat]){
            han += countDora(allTiles, getRevealedUraDoraIndicators());
            RiichiMahjong.LOGGER.info("calculateHan : 뒷도라 추가 - 현재 판수 : {}",han);
        }

        return han;
    }

    public int calculateFu(int seat){
        switchToSeat(seat);

        if(isChiitoitsu(hand, seat)){
         RiichiMahjong.LOGGER.info("calculateFu: 치또이 고정 25부");
         return 25;
        }

        if(winningGroups == null){
            return 0;   // 역만류(국사무쌍 등)는 부수 개념이 없음
        }

        List<List<Tile>> tileBundle = new ArrayList<>(winningGroups);
        tileBundle.addAll(melds);

        if(isPinfu(tileBundle, seat)){
         int pinfuFu = isTsumo ? 20 : 30;
         RiichiMahjong.LOGGER.info("calculateFu: 핑후 고정 {}부 (쯔모:{})", pinfuFu, isTsumo);
         return pinfuFu;
        }

        int fu = 20;
        RiichiMahjong.LOGGER.info("calculateFu: 기본 20부");

        for(List<Tile> group : winningGroups){
            int add = tripletFu(group, true, true);
            if(add > 0) RiichiMahjong.LOGGER.info("calculateFu: 몸통 {} → +{}", group, add);
            fu += add;
        }
        for(int i=0;i<melds.size();i++){
            MeldType type = meldTypes.get(i);
            if(type == MeldType.CHI) continue;
            boolean concealed = (type == MeldType.ANKAN);
            int add = tripletFu(melds.get(i), concealed, false);
            if(add > 0) RiichiMahjong.LOGGER.info("calculateFu: 부른패 {} ({}) → +{}", melds.get(i), type, add);
            fu += add;
        }

        List<Tile> pair = findPairGroup(winningGroups);
        if(pair != null){
            int add = pairFu(pair.get(0), seat);
            if(add > 0) RiichiMahjong.LOGGER.info("calculateFu: 머리 {} → +{}", pair.get(0), add);
            fu += add;
        }

        int wait = waitFu(winningGroups);
        if(wait > 0) RiichiMahjong.LOGGER.info("calculateFu: 대기 보너스 +{}", wait);
        fu += wait;

        if(isTsumo){
            fu += 2;
            RiichiMahjong.LOGGER.info("calculateFu: 쯔모 +2");
        } else if(isMenzenBySeat[seat]){
            fu += 10;
            RiichiMahjong.LOGGER.info("calculateFu: 멘젠론 +10");
        }

        int rounded = ((fu + 9) / 10) * 10;
        RiichiMahjong.LOGGER.info("calculateFu: 합계 {} → 올림 {}부", fu, rounded);
        return rounded;
    }

    private int tripletFu(List<Tile> group, boolean concealed, boolean checkRonDemotion){
        if(group.size() !=3 && group.size() != 4) return 0;
        Tile first = group.get(0);
        for(Tile t : group) if(!t.equals(first)) return 0;

        boolean isKan = group.size() == 4;
        boolean isTerminalOrHonor = first.getSuit() == TileSuit.HONOR || first.getNumber() == 1 || first.getNumber() == 9;

        boolean actuallyConcealed = concealed;
        if(checkRonDemotion && concealed && !isTsumo && winningTile != null && first.equals(winningTile)){
            actuallyConcealed = false;
        }

        int base = isTerminalOrHonor ? 4 : 2;
        if(actuallyConcealed) base *= 2;
        if(isKan) base *= 4;
        return base;
    }

    private List<Tile> findPairGroup(List<List<Tile>> groups){
        for(List<Tile> g : groups){
            if(g.size() == 2) return g;
        }
        return null;
    }

    private int pairFu(Tile tile, int seat){
        if(tile.getSuit() != TileSuit.HONOR) return 0;
        int fu = 0;
        if(tile.getNumber() >= 5 && tile.getNumber() <= 7) fu += 2;
        if(tile.getNumber() == seatWind[seat] + 1) fu += 2;
        if(tile.getNumber() == roundWind + 1) fu += 2;
        return fu;
    }

    private int waitFu(List<List<Tile>> groups){
        if(winningTile == null) return 0;

        for(List<Tile> group : groups){
            if(group.size() == 2){
                if(group.get(0).equals(winningTile)) return 2;
                continue;
            }

            Tile first = group.get(0);
            boolean isTriplet = true;
            for(Tile t : group){
                if(!t.equals(first)){
                    isTriplet = false;
                    break;
                }
            }
            if(isTriplet){
                continue;
            }

            boolean containsWinning = false;
            for(Tile t : group) if(t.equals(winningTile)) containsWinning = true;
            if(!containsWinning) continue;

            List<Tile> sorted = new ArrayList<>(group);
            sorted.sort(Comparator.comparingInt(Tile::getNumber));
            int a = sorted.get(0).getNumber();
            int w = winningTile.getNumber();

            if(w == a + 1) return 2;
            if((a == 1 && w == 3) || (a == 7 && w == 7)) return 2;
            return 0;
        }
        return 0;
    }

    public int sumHandForComparison(List<String> yaku, int seat) {
        switchToSeat(seat);

        if(yaku.isEmpty()) return 0;

        if(YAKUMAN_NAMES.contains(yaku.get(0))){
            int total = 0;
            for(String y : yaku){
                total += DOUBLE_YAKUMAN.contains(y) ? 26 : 13;
            }
            return total;
        }

        Map<String,Integer> table = isMenzenBySeat[seat] ? HAN_CLOSED : HAN_OPEN;
        int han = 0;
        for(String y : yaku){
            Integer h = table.get(y);
            if(h == null){
                RiichiMahjong.LOGGER.warn("calculateHan : 테이블에 없는 역 이름 '{}', 0판 처리",y);
                h = 0;
            }
            han += h;
        }
        return han;
    }

    public void switchToSeat(int seat){
        hand = hands[seat];
        discarded = discardedBySeat[seat];
        discardHistory = discardHistoryBySeat[seat];
        melds = meldsBySeat[seat];
        meldTypes = meldTypesBySeat[seat];
    }

    public boolean startGame(){
        if(state != GameState.WAITING) return false;
        dealAndStartRound();
        state = GameState.PLAYING;
        return true;
    }

    public void nextRound(){
        if(win && !dealerRepeats){
            honbaCount = 0;
        }else{
            honbaCount++;
        }

        if(!dealerRepeats){
            if(roundCount >= 4){
                matchOver = true;
                RiichiMahjong.LOGGER.info("Match is over! round over!");
                for(int i=0;i<4;i++) {
                    RiichiMahjong.LOGGER.info("seat {} score : {}",i,scoreBySeat[i]);
                }
                return;
            }
            roundCount++;
            dealerSeat = (dealerSeat + 1) % 4;
        }

        // 패산 새로 섞기
        List<Tile> fullDeck = TileDeck.shuffledDeck();
        wall.clear();
        wall.addAll(fullDeck.subList(0,122));
        deadWall.clear();
        deadWall.addAll(fullDeck.subList(122,136));

        initDoraIndicators();

        // 좌석별 상태 초기화 (seatAssignment는 그대로 유지!)
        for(int seat=0; seat<4; seat++){
            hands[seat].clear();
            discardedBySeat[seat].clear();
            discardHistoryBySeat[seat].clear();
            meldsBySeat[seat].clear();
            meldTypesBySeat[seat].clear();
            isMenzenBySeat[seat] = true;
            isRiichiBySeat[seat] = false;
            ippatsuValidBySeat[seat] = false;
            isDoubleRiichi[seat] = false;
            discardCount[seat] = 0;
            kanCountBySeat[seat] = 0;
            tempFuritenBySeat[seat] = false;
            riichiFuritenBySeat[seat] = false;
            settleScoreBySeat[seat] = 0;
        }

        kanCount = 0;
        anyCallMadeThisRound = false;
        lastDiscardOwner = -1;
        lastDiscardedTile = null;
        isRinshan = false;
        isChankan = false;
        gameEnd = false;
        win = false;
        isTsumo = false;

        dealAndStartRound();
    }

    private void dealAndStartRound(){
        for(int wind=0;wind<4;wind++){
            int windSeat = (wind + dealerSeat) % 4;
            seatWind[windSeat] = wind;
        }

        // 뽑기 로직 실제 마작처럼 변경
        for(int i=0;i<3;i++){
            for(int seat=0;seat<4;seat++){
                int drawSeat = (seat + dealerSeat) % 4;
                for(int j=0;j<4;j++){
                    hands[drawSeat].add(wall.remove(0));
                }
            }
        }
        for(int seat=0;seat<4;seat++){
            int drawSeat = (seat + dealerSeat) % 4;
            hands[drawSeat].add(wall.remove(0));
        }

        turnOrder = dealerSeat;
        drawTile(dealerSeat);

         // 손패 정리라서 순서를 오야부터 하지 않아도 됨
        for(int seat=0;seat<4;seat++){
            if(handMakeUp) handSort(hands[seat]);
        }
    }

    private List<List<List<Tile>>> getAllMeldDecompositions(List<Tile> tiles){
        List<List<List<Tile>>> results = new ArrayList<>();
        if(tiles.isEmpty()){
            results.add(new ArrayList<>());
            return results;
        }

        Tile first = tiles.get(0);

        int sameCount = 0;
        for(Tile t : tiles){
            if(t.equals(first)){
                sameCount++;
            }
        }
        if(sameCount>=3){
            List<Tile> next = new ArrayList<Tile>(tiles);
            List<Tile> group = new ArrayList<>();
            group.add(removeOne(next, first));
            group.add(removeOne(next, first));
            group.add(removeOne(next, first));
            for(List<List<Tile>> sub : getAllMeldDecompositions(next)) {
                List<List<Tile>> combined = new ArrayList<>(sub);
                combined.add(group);
                results.add(combined);
            }
        }

        if(first.getSuit() != TileSuit.HONOR){
            Tile second = new Tile(first.getSuit(), first.getNumber()+1);
            Tile third = new Tile(first.getSuit(), first.getNumber()+2);
            if(tiles.contains(second) && tiles.contains(third)){
                List<Tile> next = new ArrayList<Tile>(tiles);
                List<Tile> group = new ArrayList<>();
                group.add(removeOne(next, first));
                group.add(removeOne(next, second));
                group.add(removeOne(next, third));
                for(List<List<Tile>> sub : getAllMeldDecompositions(next)) {
                    List<List<Tile>> combined = new ArrayList<>(sub);
                    combined.add(group);
                    results.add(combined);
                }
            }
        }

        return results;
    }

    public List<List<List<Tile>>> getAllWinningGroups(List<Tile> targetHand){
        List<Tile> sorted = new ArrayList<>(targetHand);
        handSort(sorted);

        List<List<List<Tile>>> allResults = new ArrayList<>();
        Set<String> triedPairs = new HashSet<>();
        for(int i=0; i<sorted.size()-1;i++){
            Tile t1 = sorted.get(i);
            Tile t2 = sorted.get(i+1);
            if(t1.equals(t2)){
                String key = t1.getTextureId();
                if(triedPairs.contains(key)) continue;
                triedPairs.add(key);

                List<Tile> remaining = new ArrayList<>(sorted);
                remaining.remove(i+1);
                remaining.remove(i);

                for(List<List<Tile>> melds : getAllMeldDecompositions(remaining)){
                    List<List<Tile>> full = new ArrayList<>(melds);
                    full.add(List.of(t1,t2));
                    allResults.add(full);
                }
            }
        }

        return allResults;
    }

    public List<Map.Entry<String,Integer>> getYakuResultEntries(int seat){
        switchToSeat(seat);
        List<Map.Entry<String,Integer>> result = new ArrayList<>();
        List<String> yaku = getYaku(seat);
        if(yaku.isEmpty()) return result;

        if(YAKUMAN_NAMES.contains(yaku.get(0))){
            for(String y : yaku) result.add(Map.entry(y, DOUBLE_YAKUMAN.contains(y) ? 26 : 13));
            return result;
        }

        Map<String,Integer> table = isMenzenBySeat[seat] ? HAN_CLOSED : HAN_OPEN;
        for(String y : yaku) result.add(Map.entry(y, table.getOrDefault(y,0)));

        List<Tile> all = getAllTilesForSeat();
        int dora = countDora(all, getRevealedDoraIndicators());
        int aka = countAkaDora(all);
        int ura = isRiichiBySeat[seat] ? countDora(all, getRevealedUraDoraIndicators()) : 0;
        if(dora > 0) result.add(Map.entry("도라",dora));
        if(aka > 0) result.add(Map.entry("적도라",aka));
        if(ura > 0) result.add(Map.entry("뒷도라",ura));

        return result;
    }

    private void initDoraIndicators(){
        doraIndicatorPool.clear();
        uraDoraIndicatorPool.clear();
        for(int i=4;i<=12;i+=2){
            if(i<deadWall.size()) doraIndicatorPool.add(deadWall.get(i));
            if(i+1<deadWall.size()) uraDoraIndicatorPool.add(deadWall.get(i+1));
        }
        revealedDoraCount = 1;
    }

    private Tile doraFromIndicator(Tile indicator){
        if(indicator.getSuit() == TileSuit.HONOR){
            if(indicator.getNumber() <= 4){
                int next = indicator.getNumber() % 4 + 1;
                return new Tile(TileSuit.HONOR, next);
            } else {
                int next = (indicator.getNumber() - 5 + 1) % 3 + 5;
                return new Tile(TileSuit.HONOR, next);
            }
        } else {
            int next = indicator.getNumber() % 9 + 1;
            return new Tile(indicator.getSuit(), next);
        }
    }

    private List<Tile> getAllTilesForSeat(){
        List<Tile> all = new ArrayList<>(hand);
        for(List<Tile> meld : melds){
            all.addAll(meld);
        }
        return all;
    }

    private int countDora(List<Tile> allTiles, List<Tile> indicators){
        int count = 0;
        for(Tile indicator : indicators){
            Tile dora = doraFromIndicator(indicator);
            for(Tile t : allTiles){
                if(t.equals(dora)) count++;
            }
        }
        return count;
    }

    private int countAkaDora(List<Tile> allTiles){
        int count = 0;
        for(Tile t : allTiles){
            if(t.isRed()) count++;
        }
        return count;
    }

    public boolean isSuukaikan(){
        if(kanCount < MAX_KAN) return false;
        for(int i=0;i<4;i++){
            if(kanCountBySeat[i] == MAX_KAN) return false;
        }
        return true;
    }

    public boolean checkSuukaikanDraw(){
        if(gameEnd || !isSuukaikan()) return false;
        for(int i=0;i<4;i++){
            if(canRon(i)) return false;
        }
        gameEnd = true;
        win = false;
        dealerRepeats = true;
        drawReason = "사깡유국";
        return true;
    }

    private void checkHakoshita(){
        for(int i=0; i<4; i++){
            if(scoreBySeat[i]<0){
                matchOver = true;
                return;
            }
        }
    }

    private void checkMatchOver(){
        checkHakoshita();
        if(!dealerRepeats && roundCount >= 4) matchOver = true;
    }

    public boolean hasAnyCall(int seat){
        return canPon(seat) || canDaiminkan(seat) || !getChiOptionsForSeat(seat).isEmpty() || canRon(seat);
    }

    public void openCallWindow(){
        RiichiMahjong.LOGGER.info("Call CallWindow");
        int lastDiscarderSeat = lastDiscardOwner;
        for(int seat = 0;seat < 4;seat++){
            chiChoiceIndex[seat] = -1;
            if(seat == lastDiscarderSeat || !hasAnyCall(seat)) callChoices[seat] = CallChoice.NONE;
            else if(isSeatAssigned(seat)) callChoices[seat] = CallChoice.PENDING;
            else callChoices[seat] = CallChoice.PASS;
        }
    }

    public boolean hasPendingCall(){
        for(CallChoice choice : callChoices) if(choice == CallChoice.PENDING) return true;
        return false;
    }

    public void clearCallWindow(){
        RiichiMahjong.LOGGER.info("clearCallWindow 호출됨");
        for(int seat = 0;seat < 4;seat++){
            callChoices[seat] = CallChoice.NONE;
            chiChoiceIndex[seat] = -1;
        }
        RiichiMahjong.LOGGER.info("Call Window : {}",java.util.Arrays.toString(callChoices));
    }

    // MahjongScreen의 hasCallOption 바꾸기 전까지 clearCallState 함께 사용하기
    public boolean resolveCalls(){
        int discarder = lastDiscardOwner;

        for(int i=1;i<4;i++){
            int seat = (discarder + i) % 4;
            if(callChoices[seat] == CallChoice.RON){
                declareRon(seat);
                clearCallState();
                clearCallWindow();
                return true;
            }
        }

        for(int seat=0;seat<4;seat++){
            if(callChoices[seat] == CallChoice.PON){
                callPon(seat);
                clearCallState();
                clearCallWindow();
                return true;
            }
            if(callChoices[seat] == CallChoice.DAIMINKAN){
                callDaiminkan(seat);
                clearCallState();
                clearCallWindow();
                return true;
            }
        }

        for(int seat=0;seat<4;seat++){
            if(callChoices[seat] == CallChoice.CHI){
                List<Integer> option = getChiOptionsForSeat(seat).get(chiChoiceIndex[seat]);
                callChi(seat, option.get(0), option.get(1));
                clearCallState();
                clearCallWindow();
                return true;
            }
        }
        clearCallState();
        clearCallWindow();
        for(int seat=0;seat<4;seat++){
            if(callChoices[seat] == CallChoice.PASS && canRon(seat)){
                if(isRiichi(seat)) setRiichiFuriten(seat); else setTempFuriten(true, seat);
            }
        }
        if(checkSuukaikanDraw()){
            return true;
        }
        return false;
    }

    public CompoundTag toNBT(){
        CompoundTag tag = new CompoundTag();
        tag.put("wall",tileListToNBT(wall));
        tag.put("deadWall", tileListToNBT(deadWall));
        tag.put("hand", tileListToNBT(hand));
        tag.put("discarded", tileListToNBT(discarded));
        tag.putBoolean("gameEnd", gameEnd);
        tag.putBoolean("win", win);

        tag.put("doraIndicatorPool",tileListToNBT(doraIndicatorPool));
        tag.put("uraDoraIndicatorPool",tileListToNBT(uraDoraIndicatorPool));
        tag.putInt("revealedDoraCount",revealedDoraCount);

        tag.putBoolean("handMakeUp", handMakeUp);
        tag.putBoolean("isTsumo", isTsumo);
        tag.putInt("turnOrder", turnOrder);
        tag.putInt("kanCount", kanCount);
        tag.putIntArray("kanCountBySeat",kanCountBySeat);
        tag.putBoolean("isRinshan", isRinshan);
        tag.putBoolean("isChankan", isChankan);
        tag.putInt("lastDiscardOwner", lastDiscardOwner);
        tag.putInt("lastWinSeat", lastWinSeat);
        tag.putInt("lastWinHan",lastWinHan);
        tag.putInt("lastWinFu",lastWinFu);
        tag.putInt("lastWinScores",lastWinScores);
        tag.putString("lastWinLabel",lastWinLabel);
        tag.putInt("dealerSeat",dealerSeat);
        tag.putBoolean("dealerRepeats", dealerRepeats);
        tag.putInt("honbaCount", honbaCount);
        tag.putInt("roundCount", roundCount);
        tag.putBoolean("matchOver", matchOver);

        CompoundTag seatAssignmentTag = new CompoundTag();
        for(Map.Entry<UUID, Integer> entry : seatAssignment.entrySet()){
            seatAssignmentTag.putInt(entry.getKey().toString(),entry.getValue());
        }
        tag.put("seatAssignment",seatAssignmentTag);
        tag.putString("gameState",state.name());

        int[] doubleRiichiInt = new int[4];
        int[] menzenInt = new int[4];
        int[] riichiInt = new int[4];
        int[] ippatsuInt = new int[4];
        for(int i=0;i<4;i++){
            doubleRiichiInt[i] = isDoubleRiichi[i] ? 1 : 0;
            menzenInt[i] = isMenzenBySeat[i] ? 1 : 0;
            riichiInt[i] = isRiichiBySeat[i] ? 1 : 0;
            ippatsuInt[i] = ippatsuValidBySeat[i] ? 1 : 0;
        }
        tag.putIntArray("scoreBySeat",scoreBySeat);
        tag.putIntArray("settleScoreBySeat",settleScoreBySeat);
        tag.putIntArray("isDoubleRiichi", doubleRiichiInt);
        tag.putIntArray("isMenzenBySeat", menzenInt);
        tag.putIntArray("isRiichiBySeat", riichiInt);
        tag.putIntArray("ippatsuValidBySeat", ippatsuInt);
        tag.putIntArray("discardCount", discardCount);

        if(winningTile != null) tag.put("winningTile", winningTile.toNBT());
        if(drawnTile != null) tag.put("drawnTile", drawnTile.toNBT());

        // melds 저장
        ListTag handsTag = new ListTag();
        ListTag discardedTag = new ListTag();
        ListTag discardHistoryTag = new ListTag();
        ListTag allMeldsTag = new ListTag();
        for(int seat=0;seat<4;seat++){
            handsTag.add(tileListToNBT(hands[seat]));
            discardedTag.add(tileListToNBT(discardedBySeat[seat]));
            discardHistoryTag.add(tileListToNBT(discardHistoryBySeat[seat]));

            ListTag seatMeldsTag = new ListTag();
            for(int i=0;i<meldsBySeat[seat].size();i++){
                CompoundTag meldTag = new CompoundTag();
                meldTag.put("tiles", tileListToNBT(meldsBySeat[seat].get(i)));
                meldTag.putString("type", meldTypesBySeat[seat].get(i).name());
                seatMeldsTag.add(meldTag);
            }
            allMeldsTag.add(seatMeldsTag);
        }
        tag.put("hands",handsTag);
        tag.put("discardedBySeat",discardedTag);
        tag.put("discardHistoryBySeat",discardHistoryTag);
        tag.put("meldsBySeat",allMeldsTag);

        return tag;
    }

    public static MahjongGame fromNBT(CompoundTag tag){
        MahjongGame game = new MahjongGame(true);
        game.wall.addAll(nbtToTileList(tag.getList("wall", 10)));
        game.deadWall.addAll(nbtToTileList(tag.getList("deadWall", 10)));
        game.gameEnd = tag.getBoolean("gameEnd");
        game.win = tag.getBoolean("win");

        game.doraIndicatorPool.addAll(nbtToTileList(tag.getList("doraIndicatorPool",10)));
        game.uraDoraIndicatorPool.addAll(nbtToTileList(tag.getList("uraDoraIndicatorPool",10)));
        game.revealedDoraCount = tag.contains("revealedDoraCount") ? tag.getInt("revealedDoraCount") : 1;

        game.handMakeUp = tag.getBoolean("handMakeUp");
        game.isTsumo = tag.getBoolean("isTsumo");
        game.turnOrder = tag.getInt("turnOrder");
        game.kanCount = tag.getInt("kanCount");
        int[] saved = tag.getIntArray("kanCountBySeat");
        if(saved.length == 4){
            for(int i=0;i<saved.length;i++){
                game.kanCountBySeat[i] = saved[i];
            }
        }
        game.isRinshan = tag.getBoolean("isRinshan");
        game.isChankan = tag.getBoolean("isChankan");
        game.lastDiscardOwner = tag.getInt("lastDiscardOwner");
        game.lastWinSeat = tag.getInt("lastWinSeat");
        game.lastWinHan = tag.getInt("lastWinHan");
        game.lastWinFu = tag.getInt("lastWinFu");
        game.lastWinScores = tag.getInt("lastWinScores");
        game.lastWinLabel = tag.getString("lastWinLabel");
        game.dealerSeat = tag.getInt("dealerSeat");
        game.dealerRepeats = tag.getBoolean("dealerRepeats");
        game.honbaCount = tag.getInt("honbaCount");
        game.roundCount = tag.getInt("roundCount");
        game.matchOver = tag.getBoolean("matchOver");

        CompoundTag seatAssignmentTag = tag.getCompound("seatAssignment");
        for(String key : seatAssignmentTag.getAllKeys()){
            game.seatAssignment.put(UUID.fromString(key),seatAssignmentTag.getInt(key));
        }
        String stateStr = tag.getString("gameState");
        if(!stateStr.isEmpty()){
            game.state = GameState.valueOf(stateStr);
        }

        game.scoreBySeat = tag.getIntArray("scoreBySeat");
        game.settleScoreBySeat = tag.getIntArray("settleScoreBySeat");
        int[] doubleRiichiInt = tag.getIntArray("isDoubleRiichi");
        for(int i=0;i<4 && i<doubleRiichiInt.length;i++) game.isDoubleRiichi[i] = doubleRiichiInt[i] == 1;
        int[] menzenInt = tag.getIntArray("isMenzenBySeat");
        for(int i=0;i<4 && i<menzenInt.length;i++) game.isMenzenBySeat[i] = menzenInt[i] == 1;
        int[] riichiInt = tag.getIntArray("isRiichiBySeat");
        for(int i=0;i<4 && i<riichiInt.length;i++) game.isRiichiBySeat[i] = riichiInt[i] == 1;
        int[] ippatsuInt = tag.getIntArray("ippatsuValidBySeat");
        for(int i=0;i<4 && i<ippatsuInt.length;i++) game.ippatsuValidBySeat[i] = ippatsuInt[i] == 1;
        int[] savedDiscardCount = tag.getIntArray("discardCount");
        for(int i=0;i<4 && i<savedDiscardCount.length;i++) game.discardCount[i] = savedDiscardCount[i];

        if(tag.contains("winningTile")) game.winningTile = Tile.fromNBT(tag.getCompound("winningTile"));
        if(tag.contains("drawnTile")) game.drawnTile = Tile.fromNBT(tag.getCompound("drawnTile"));

        // melds 복원
        ListTag handsTag = tag.getList("hands",9);
        ListTag discardedTag = tag.getList("discardedBySeat",9);
        ListTag discardHistoryTag = tag.getList("discardHistoryBySeat",9);
        ListTag allMeldsTag = tag.getList("meldsBySeat", 9);

        for(int seat = 0; seat < 4 && seat < handsTag.size(); seat++){
            game.hands[seat].addAll(nbtToTileList((ListTag)handsTag.get(seat)));
            game.discardedBySeat[seat].addAll(nbtToTileList((ListTag)discardedTag.get(seat)));
            game.discardHistoryBySeat[seat].addAll(nbtToTileList((ListTag)discardHistoryTag.get(seat)));

            ListTag seatMeldsTag = (ListTag) allMeldsTag.get(seat);
            for(int i=0;i<seatMeldsTag.size();i++){
                CompoundTag meldTag = seatMeldsTag.getCompound(i);
                game.melds.add(nbtToTileList(meldTag.getList("tiles", 10)));
                game.meldTypes.add(MeldType.valueOf(meldTag.getString("type")));
            }
        }

        // 임시로 0으로 놓음
        game.hand = game.hands[0];
        game.discarded = game.discardedBySeat[0];
        game.discardHistory = game.discardHistoryBySeat[0];
        game.melds = game.meldsBySeat[0];
        game.meldTypes = game.meldTypesBySeat[0];

        return game;
    }

    public int assignSeat(UUID uuid){
        if(seatAssignment.containsKey(uuid)) {
            return seatAssignment.get(uuid);
        }
        if(state != GameState.WAITING){
            return -1;
        }
        for(int seat=0;seat<4;seat++){
            if(!seatAssignment.containsValue(seat)){
                seatAssignment.put(uuid,seat);
                return seat;
            }
        }
        return -1;  // 자리 없는 경우
    }

    public boolean isSeatAssigned(int seat){
        return seatAssignment.containsValue(seat);
    }

    public static ListTag tileListToNBT(List<Tile> tiles){
        ListTag list = new ListTag();
        for(Tile t : tiles){
            list.add(t.toNBT());
        }
        return list;
    }

    public static List<Tile> nbtToTileList(ListTag list){
        List<Tile> tiles = new ArrayList<>();
        for(int i=0;i<list.size();i++){
            tiles.add(Tile.fromNBT(list.getCompound(i)));
        }
        return tiles;
    }

    // 판별 점수 계산, 부수 계산 이후 만들기
    public int calculateHandScore(int seat){
        switchToSeat(seat);
        List<String> yaku = getYaku(seat);
        if(yaku.isEmpty()) return 0;

        if(YAKUMAN_NAMES.contains(yaku.get(0))){
            int yakumanCount = 0;
            for(String y : yaku){
                yakumanCount += DOUBLE_YAKUMAN.contains(y) ? 2 : 1;
            }
            return 8000 * yakumanCount;
        }

        int han = calculateHan(seat);

        if(han >= 13) return 8000;   // 헤아림역만
        if(han >= 11) return 6000;   // 삼배만
        if(han >= 8)  return 4000;   // 배만
        if(han >= 6)  return 3000;   // 하네만
        if(han == 5)  return 2000;   // 만관

        int fu = calculateFu(seat);
        int base = fu * (int)Math.pow(2, 2 + han);
        return Math.min(base, 2000);   // 2000 넘으면 만관으로 올림
    }

    // 잔고 반영
    public void settleScores(int seat){
        int handScore = calculateHandScore(seat);
        RiichiMahjong.LOGGER.info("handScore : {}",handScore);
        if(isTsumo){
            if(seat == dealerSeat){
                int payment = roundUp100(handScore * 2) + 100 * honbaCount;
                for(int i=0;i<4;i++){
                    if(i != seat){
                        scoreBySeat[i] -= payment;
                        scoreBySeat[seat] += payment;
                        settleScoreBySeat[i] -= payment;
                        settleScoreBySeat[seat] += payment;
                    }
                }
                RiichiMahjong.LOGGER.info("dealer tsumo payment : {}",payment);
            }else{
                int dealerPayment = roundUp100(handScore * 2) + 100 * honbaCount;
                int otherPayment = roundUp100(handScore) + 100 * honbaCount;
                for(int i=0;i<4;i++){
                    if(i != seat){
                        int payment = (i == dealerSeat) ? dealerPayment : otherPayment;
                        scoreBySeat[i] -= payment;
                        scoreBySeat[seat] += payment;
                        settleScoreBySeat[i] -= payment;
                        settleScoreBySeat[seat] += payment;
                        RiichiMahjong.LOGGER.info("tsumo payment : {}",payment);
                    }
                }
            }
        }else{
            int multiplier = (seat == dealerSeat) ? 6 : 4;
            int payment = roundUp100(handScore * multiplier) + 300 * honbaCount;
            scoreBySeat[lastDiscardOwner] -= payment;
            scoreBySeat[seat] += payment;
            settleScoreBySeat[lastDiscardOwner] -= payment;
            settleScoreBySeat[seat] += payment;
            RiichiMahjong.LOGGER.info("dealer : {}, ron payment : {}",seat == dealerSeat,payment);
        }

        scoreBySeat[seat] += riichiStick * 1000;
        settleScoreBySeat[seat] += riichiStick * 1000;

        lastWinSeat = seat;
        lastWinHan = calculateHan(seat);
        lastWinFu = calculateFu(seat);
        lastWinScores = roundUp100(handScore * ((seat == dealerSeat) ? 6 : 4));
        lastWinLabel = winLabel(seat);
        for(int i=0;i<4;i++){
            RiichiMahjong.LOGGER.info("seat {} score : {}",i,scoreBySeat[i]);
        }
        riichiStick = 0;
        checkMatchOver();
    }

    private int roundUp100(int points){
        return ((points + 99) / 100) * 100;
    }

    private void settleDrawScores(){
        boolean[] tenpaiBySeat = new boolean[4];
        int tenpaiCount = 0;
        for(int i=0;i<4;i++){
            tenpaiBySeat[i] = isTenpai(new ArrayList<>(hands[i]), i);
            if(tenpaiBySeat[i]) tenpaiCount++;
        }

        if(tenpaiCount == 0 || tenpaiCount == 4) return;

        int notenCount = 4 - tenpaiCount;
        int tenpaiGain = 3000 / tenpaiCount;
        int notenLoss = 3000 / notenCount;

        for(int i=0;i<4;i++){
            if(tenpaiBySeat[i]) {
                scoreBySeat[i] += tenpaiGain;
                settleScoreBySeat[i] += tenpaiGain;
            }else{
                scoreBySeat[i] -= notenLoss;
                settleScoreBySeat[i] -= notenLoss;
            }
        }
        checkMatchOver();
    }

    private void settleNagashiMangan(){
        for(int seat=0;seat<4;seat++){
            if(isNagashiMangan(seat)){
                for(int i=0;i<4;i++){
                    if(i == seat) continue;
                    int pay = (seat == dealerSeat) ? 4000 : (i == dealerSeat ? 4000 : 2000);
                    scoreBySeat[i] -= pay;
                    scoreBySeat[seat] += pay;
                }
            }
        }
    }

    public List<Tile> getWaitingTiles(List<Tile> targetHand, int seat){
        List<Tile> waits = new ArrayList<>();
        for (TileSuit suit : new TileSuit[]{TileSuit.MAN, TileSuit.SOU, TileSuit.PIN}) {
            for (int n = 1; n <= 9; n++) {
                Tile candidate = new Tile(suit, n);
                List<Tile> testHand = new ArrayList<>(targetHand);
                testHand.add(candidate);
                if(isKokushi(testHand, seat) || isChiitoitsu(testHand, seat) || getWinningGroups(testHand) != null){
                    waits.add(candidate);
                }
            }
        }
        for(int n = 1; n <= 7; n++){
            Tile candidate = new Tile(TileSuit.HONOR, n);
            List<Tile> testHand = new ArrayList<>(targetHand);
            testHand.add(candidate);
            if(isKokushi(testHand, seat) || isChiitoitsu(testHand, seat) || getWinningGroups(testHand) != null){
                waits.add(candidate);
            }
        }
        return waits;
    }

    private String winLabel(int seat){
        List<String> yaku = getYaku(seat);
        if(!yaku.isEmpty() && YAKUMAN_NAMES.contains(yaku.get(0))){
            int count = 0;
            for(String y : yaku) count += DOUBLE_YAKUMAN.contains(y) ? 2 : 1;
            return count == 1 ? "역만" : count == 2 ? "더블 역만" : count + "배 역만";
        }
        int han = calculateHan(seat);
        if(han >= 13) return "헤아림역만";
        if(han >= 11) return "삼배만";
        if(han >= 8)  return "배만";
        if(han >= 6)  return "하네만";
        if(han == 5 || calculateHandScore(seat) >= 2000)  return "만관";
        return "";
    }

    public boolean isDiscardFuriten(int seat){
        List<Tile> baseHand = new ArrayList<>(hands[seat]);
        if(baseHand.size() == 14 && drawnTile != null){
            baseHand.remove(drawnTile);
        }

        List<Tile> waits = getWaitingTiles(baseHand, seat);
        for(Tile wait : waits){
            if(discardHistoryBySeat[seat].contains(wait)) return true;
        }
        return false;
    }

    public boolean isFuriten(int seat){
        return isDiscardFuriten(seat) || tempFuritenBySeat[seat] || riichiFuritenBySeat[seat];
    }

    public boolean isNagashiMangan(int seat){
        if(discardedBySeat[seat].size() != discardHistoryBySeat[seat].size()) return false;

        for(Tile t : discardHistoryBySeat[seat]){
            boolean isTerminalOrHonor = t.getSuit() == TileSuit.HONOR || t.getNumber() == 1 || t.getNumber() == 9;
            if(!isTerminalOrHonor) return false;
        }

        return true;
    }

    public UUID getUuidOfSeat(int seat){
        for(Map.Entry<UUID, Integer> entry : seatAssignment.entrySet()){
            if(entry.getValue() == seat) return entry.getKey();
        }
        return null;
    }

    public boolean releaseSeat(UUID uuid){
        if(state != GameState.WAITING) return false;    // 게임 진행 중 자리 유지
        return seatAssignment.remove(uuid) != null;
    }

    public void handSort(List<Tile> hand){
        hand.sort((a, b) -> {
            if(a.getSuit()!=b.getSuit()){
                return a.getSuit().compareTo(b.getSuit());
            }
            return Integer.compare(a.getNumber(), b.getNumber());
        });
    }

    public List<Tile> getHand() {
        return hand;
    }

    public List<Tile> getDiscarded() {
        return discarded;
    }

    public List<List<Tile>> getMelds() { return melds; }

    public List<MeldType> getMeldTypes() { return meldTypes; }

    public int getRoundWind() { return roundWind; }

    public boolean isHandMakeUp() { return handMakeUp; }

    public boolean isGameEnd(){
        return gameEnd;
    }

    public boolean isWin() { return win; }

    public boolean isMenzen(int seat) { return isMenzenBySeat[seat]; }

    public boolean isRiichi(int seat) { return isRiichiBySeat[seat]; }

    public boolean isTsumo() { return isTsumo; }

    public int getTurnOrder() { return turnOrder; }

    public Tile getWinningTile() { return winningTile; }

    public Tile getDrawnTile() { return drawnTile; }

    public Tile getLastDiscardedTile() { return lastDiscardedTile; }

    public int getWallSize() {
        return wall.size();
    }

    public int getHandSizeOfSeat(int seat) { return hands[seat].size(); }

    public List<Tile> getDiscardedOfSeat(int seat) { return discardedBySeat[seat]; }

    public Integer getSeatOf(UUID uuid) { return seatAssignment.get(uuid); }

    public int[] getScoreBySeat() { return scoreBySeat; }

    public int[] getSettleScoreBySeat() { return settleScoreBySeat; }

    public GameState getGameState() { return state; }

    public boolean canProceedToNextRound() { return gameEnd && !matchOver; }

    public int getDealerSeat() { return dealerSeat; }

    public int getHonbaCount() { return honbaCount; }

    public boolean isDealerRepeats() { return dealerRepeats; }

    public int getRoundCount() { return roundCount; }

    public String getDrawReason() { return drawReason; }

    public boolean isMatchOver() { return matchOver; }

    public List<Tile> getRevealedDoraIndicators(){
        return new ArrayList<>(doraIndicatorPool.subList(0,Math.min(revealedDoraCount,doraIndicatorPool.size())));
    }

    public List<Tile> getRevealedUraDoraIndicators(){
        return new ArrayList<>(uraDoraIndicatorPool.subList(0,Math.min(revealedDoraCount,uraDoraIndicatorPool.size())));
    }

    public int getWinSeat() { return lastWinSeat; }

    public int getWinHan() { return lastWinHan; }

    public int getWinFu() { return lastWinFu; }

    public int getWinScores() { return lastWinScores; }

    public String getWinLabel() { return lastWinLabel; }

    public int getCanCallCount() { return canCallCount; }

    public int getDeclareCallCount() { return declareCallCount; }

    public CallChoice[] getCallChoices(){ return callChoices.clone(); }

    public void setHandMakeUp(boolean handMakeUp) { this.handMakeUp = handMakeUp; }

//    public void resetToPerspective(int seat){
//        switchToSeat(seat);
//    }

    public void setSelfCallPassed(boolean selfCallPassed) { this.selfCallPassed = selfCallPassed; }

    // 화료 상태일 때 호출되기 때문에 win을 함께 사용
    public void setGameEnd(int seat){
        gameEnd = true;
        win = true;
        if(turnOrder == seat) isTsumo = true;
    }

    public void setRiichiFuriten(int seat){
        riichiFuritenBySeat[seat] = true;
    }

    public void setTempFuriten(boolean furiten, int seat){
        tempFuritenBySeat[seat] = furiten;
    }

    public void setCanCallCount(int canCallCount) { this.canCallCount = canCallCount; }

    public void setDeclareCallCount(int declareCallCount) { this.declareCallCount = declareCallCount; }

    public void setCallChoice(int seat, CallChoice choice, int chiIndex) {
        this.callChoices[seat] = choice;
        this.chiChoiceIndex[seat] = chiIndex;
    }

    public boolean declareKyuushuKyuuhai(int seat){
        if(!canDeclareKyuushu(seat)) return false;

        gameEnd = true;
        win = false;
        dealerRepeats = true;
        drawReason = "구종구패";
        return true;
    }
}
