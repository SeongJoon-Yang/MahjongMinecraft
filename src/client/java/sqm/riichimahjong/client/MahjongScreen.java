package sqm.riichimahjong.client;

import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Quaternionf;
import sqm.riichimahjong.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MahjongScreen extends AbstractContainerScreen<MahjongMenu> {
    private static final ResourceLocation BACKGROUND_TEXTURE =
            new ResourceLocation("riichi-mahjong","textures/gui/mahjong_table.png");
    private static final ResourceLocation PANEL_TEXTURE =
            new ResourceLocation("riichi-mahjong","textures/gui/mahjong_panel.png");
    private static final ResourceLocation TILE_BACK =
            new ResourceLocation("riichi-mahjong", "textures/gui/tiles/tile_back.png");
    private static final ResourceLocation SCORE_PANEL_TEXTURE =
            new ResourceLocation("riichi-mahjong","textures/gui/navy_plate.png");
    private static final ResourceLocation MINUS_DIGIT_TEXTURE =
            new ResourceLocation("riichi-mahjong","textures/gui/numbers/minus.png");
    private static final ResourceLocation POSITIVE_DIGIT_TEXTURE =
            new ResourceLocation("riichi-mahjong","textures/gui/numbers/positive/plus.png");
    private static final ResourceLocation NEGATIVE_DIGIT_TEXTURE =
            new ResourceLocation("riichi-mahjong","textures/gui/numbers/negative/minus.png");
    private static final ResourceLocation INFO_TEXTURE =
            new ResourceLocation("riichi-mahjong","textures/gui/info.png");

    private static final UUID[] DUMMY_UUIDS = {
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000004")
    };

    private static final String[] DUMMY_NAMES = {
            "더미1",
            "더미2",
            "더미3",
            "더미4"
    };

    private static final long SCORE_APPLY_DELAY = 2000;   // 점수 반영
    private static final long NEXT_BUTTON_DELAY  = 2000;   // 다음 버튼
    private static final long CONFIRM_BUTTON_DELAY = 4000; // 확인 버튼
    private static final long MATCH_OVER_DELAY = 2000;  // 매칭 종료 확인 버튼

    private static final int LOGICAL_W = 256;
    private int panelX, panelY;
    private float scale;

    private static final float TABLE_SCALE = 1.3f;
    private int tableX, tableY, tableW, tableH;

    private static final int TILE_WIDTH = 12;
    private static final int TILE_HEIGHT = 16;

    private static final int SCORE_TEXTURE_WIDTH = 6;
    private static final int SCORE_TEXTURE_HEIGHT = 8;

    private int handStartX;
    private int handStartY;
    private static final int TILE_SPACING = 14;

    private int buttonStartX;
    private int buttonStartY;
    private int makeUpTextWidth;

    private boolean riichiMode = false;
    private boolean nextRoundSent = false;
    private enum PopupType { NONE, CHI, KAN }
    private PopupType popupType = PopupType.NONE;

    // 깡 팝업용: 어떤 종류의 깡인지 옵션마다 같이 저장 (true = 가깡, false = 암깡)
    private List<Tile> kanPopupTiles = new ArrayList<>();
    private List<Boolean> kanPopupIsKakan = new ArrayList<>();

    private int callButtonStartX;
    private int callButtonStartY;
    private static final int CALL_BUTTON_WIDTH = 36;
    private static final int CALL_BUTTON_HEIGHT = 16;
    private static final int CALL_BUTTON_GAP = 4;

    private String confirmText;
    private int confirmWidth;
    private int confirmButtonX;
    private int confirmButtonY;

    private boolean wasGameEnd = false;
    private long resultShownAt;
    private int resultPage = 0;
    private boolean showInfo = false;

    private final float[] rotations = {0f, -90f, 180f, 90f};

    public MahjongScreen(MahjongMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init(){
        double aspect = 256.0 / 200.0;
        int maxW = (int)(this.width * 0.9);
        int maxH = (int)(this.height * 0.9);

        if(maxW / aspect <= maxH){
            this.imageWidth = maxW;
            this.imageHeight = (int)(maxW / aspect);
        } else {
            this.imageHeight = maxH;
            this.imageWidth = (int)(maxH * aspect);
        }

        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        updateLayout();
        // 텍스처 밖 영역이 비지 않게 바탕을 먼저 채움
        guiGraphics.fill(0,0,this.width,this.height,0xFF0B1E33);

        guiGraphics.blit(BACKGROUND_TEXTURE, tableX, tableY,0, 0, tableW, tableH, tableW, tableH);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        updateLayout();

        MahjongGame.GameState state = this.menu.getGameState();
        List<Tile> hand = this.menu.getPlayerHand();
        int mySeat = this.menu.getViewSeat();

        int size = sx(70);
        int tPanelX = px(128) - size / 2;
        int tPanelY = py(100) - size / 2;
        float centerX = tPanelX + size / 2f;
        float centerY = tPanelY + size / 2f;
        int inset = sx(1);

        String[] seatWind = {"동","남","서","북"};
        int windBox = sx(18);
        int windX = tPanelX + inset;
        int windY = tPanelY + size - inset - windBox;

        guiGraphics.blit(PANEL_TEXTURE, tPanelX, tPanelY, 0, 0, size,size,size,size);

        for(int seat=0;seat<4;seat++) {
            int rel = (seat - mySeat + 4) % 4;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY, 0);
            guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotations[rel]));
            guiGraphics.pose().translate(-centerX, -centerY, 0);

            int seatWindWidth = textW(seatWind[seat]);

            guiGraphics.fill(windX,windY,windX + windBox,windY + windBox,0xFF595A61);
            guiGraphics.fill(windX + sx(1),windY + sx(1),windX + windBox - sx(1),windY + windBox - sx(1),seatWind[seat].equals("동") ? 0xFFFF0000 : 0xFF000000);
            drawTextAt(guiGraphics, seatWind[seat], windX + (windBox - seatWindWidth) / 2, windY + (windBox - sx(8)) / 2, seatWind[seat].equals("동") ? 0x000000 : 0xFFFFFF);

            guiGraphics.pose().popPose();
        }

        if(state == MahjongGame.GameState.WAITING){
            renderWaitingPage(guiGraphics);
        }
        if(state == MahjongGame.GameState.PLAYING) {
            renderMahjongPage(guiGraphics, mouseX, mouseY, hand);

            if (this.menu.isGameEnd()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 10);

                if (resultPage == 0) {
                    renderResultPage(guiGraphics, hand);
                } else if (resultPage == 1) {
                    renderSettleScorePage(guiGraphics);
                } else {
                    renderMatchOverPage(guiGraphics);
                }

                guiGraphics.pose().popPose();
            }
        }

        // 망겜 초기화용
//        guiGraphics.fill(px(-10),py(-10),px(-5),py(-5),0xFFFFFFFF);

        // gui 디버깅용
//        guiGraphics.fill(px(128), 0, px(128) + 1, this.height, 0xFFFF00FF);   // 가로 중심선
//        guiGraphics.fill(0, py(100), this.width, py(100) + 1, 0xFFFF00FF);    // 세로 중심선
    }

    @Override
    public void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY){
        // 기본 제목/Inventory 텍스트 없애려면 비워두기
        // 원하는 위치에 텍스트를 그리려면 guiGraphics.drawString(...) 추가
        // guiGraphics.drawString(this.font,"마작 탁자",8,6,0x3f3f3f,false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(this.menu.getGameState() == MahjongGame.GameState.WAITING){
            int mySeat = this.menu.getMySeat();

            String gameTitle = "4인동풍전";
            int titleWidth = textW(gameTitle);
            int infoSize = sx(10);
            int infoX = px(128) + titleWidth / 2 + sx(8);
            int infoY = py(0) + (sx(18) - infoSize) / 2;

            showInfo = mouseX >= infoX && mouseX <= infoX + infoSize && mouseY >= infoY && mouseY <= infoY + infoSize;

            String startMahjongText = "시작";
            int startMahjongWidth = textW(startMahjongText);
            int startMahjongButtonX = px(128) - startMahjongWidth / 2;
            int startMahjongButtonY = py(190);

            if (mouseX >= startMahjongButtonX - sx(5) && mouseX <= startMahjongButtonX + startMahjongWidth + sx(5)
                    && mouseY >= startMahjongButtonY - sx(4) && mouseY <= startMahjongButtonY + sx(12) && mySeat == 0) {
                // state를 playing으로 만들라는 패킷 보내기
                sendMatchStartPacket();
            }
        }
        if(this.menu.getGameState() == MahjongGame.GameState.PLAYING) {
            if (popupType != PopupType.NONE) {
                int popupX = px(88);

                int optionCount = popupType == PopupType.CHI
                        ? this.menu.getChiOptions().size()
                        : kanPopupTiles.size();

                for (int i = 0; i < optionCount; i++) {
                    int optionY = py(70 + i * 18);
                    if (mouseX >= popupX && mouseX <= popupX + sx(80) && mouseY >= optionY && mouseY <= optionY + sx(16)) {
                        if (popupType == PopupType.CHI) {
                            List<Integer> option = this.menu.getChiOptions().get(i);
                            sendChiPacket(option.get(0), option.get(1));
                        } else {
                            Tile tile = kanPopupTiles.get(i);
                            if (kanPopupIsKakan.get(i)) {
                                sendKakanPacket(tile);
                            } else {
                                sendAnkanPacket(tile);
                            }
                        }
                        popupType = PopupType.NONE;
                        return true;
                    }
                }
                popupType = popupType.NONE;
                return true;
            }

            // ===== 콜 버튼 클릭 =====
            if ((this.menu.canDeclareKyuushu() || this.menu.canPon() || this.menu.canChi()
                    || this.menu.canDaiminkan() || this.menu.canAnkan() || this.menu.canKakan()
                    || this.menu.canRiichi() || this.menu.canTsumo() || this.menu.canRon())
                    && !this.menu.isGameEnd()) {

                int x = callButtonStartX;

                if (riichiMode) {
                    if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                        riichiMode = false;
                        return true;
                    }
                } else {
                    if (this.menu.canDeclareKyuushu()) {
                        if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                            sendKyuushuPacket();
                            riichiMode = false;
                            return true;
                        }
                        x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                    }
                    if (this.menu.canPon()) {
                        if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                            sendPonPacket();
                            riichiMode = false;
                            return true;
                        }
                        x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                    }
                    if (this.menu.canChi()) {
                        if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                            List<List<Integer>> options = this.menu.getChiOptions();
                            if (options.size() == 1) {
                                sendChiPacket(options.get(0).get(0), options.get(0).get(1));
                            } else {
                                popupType = PopupType.CHI;
                            }
                            riichiMode = false;
                            return true;
                        }
                        x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                    }
                    if (this.menu.canDaiminkan() || this.menu.canAnkan() || this.menu.canKakan()) {
                        if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                            if (this.menu.canDaiminkan()) {
                                sendDaiminkanPacket();
                            } else {
                                kanPopupTiles.clear();
                                kanPopupIsKakan.clear();
                                for (Tile t : this.menu.getAnkanOptions()) {
                                    kanPopupTiles.add(t);
                                    kanPopupIsKakan.add(false);
                                }
                                for (Tile t : this.menu.getKakanOptions()) {
                                    kanPopupTiles.add(t);
                                    kanPopupIsKakan.add(true);
                                }
                                if (kanPopupTiles.size() == 1) {
                                    if (kanPopupIsKakan.get(0)) sendKakanPacket(kanPopupTiles.get(0));
                                    else sendAnkanPacket(kanPopupTiles.get(0));
                                } else if (!kanPopupTiles.isEmpty()) {
                                    popupType = PopupType.KAN;
                                }
                            }
                            riichiMode = false;
                            return true;
                        }
                        x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                    }
                    if (this.menu.canRiichi()) {
                        if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                            riichiMode = !riichiMode;
                            return true;
                        }
                        x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                    }
                    if (this.menu.canTsumo()) {
                        if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                            sendTsumoPacket();
                            return true;
                        }
                        x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                    }
                    if (this.menu.canRon()) {
                        if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                            sendRonPacket();
                            return true;
                        }
                        x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                    }
                    if (isWithinCallButton(mouseX, mouseY, x, callButtonStartY)) {
                        riichiMode = false;
                        sendPassPacket();
                        return true;
                    }
                }
            }

            if (this.menu.isGameEnd()) {
                long delay;
                switch (resultPage) {
                    case 0 -> delay = NEXT_BUTTON_DELAY;
                    case 1 -> delay = CONFIRM_BUTTON_DELAY;
                    case 2 -> delay = MATCH_OVER_DELAY;
                    default -> delay = 10000000;
                }

                if (hasElapsed(delay)) {
                    String confirmText = (resultPage == 0) ? "다음" : "확인";
                    int confirmWidth = textW(confirmText);
                    int confirmButtonX = px(244) - confirmWidth;
                    int confirmButtonY = py(180);

                    if (mouseX >= confirmButtonX - sx(6) && mouseX <= confirmButtonX + confirmWidth + sx(6)
                            && mouseY >= confirmButtonY - sx(5) && mouseY <= confirmButtonY + sx(11)) {
                        switch (resultPage) {
                            case 0 -> {
                                resultShownAt = System.currentTimeMillis();
                                resultPage = 1;
                            }
                            case 1 -> {
                                if (this.menu.isMatchOver()) {
                                    resultShownAt = System.currentTimeMillis();
                                    resultPage = 2;
                                } else {
                                    if (!nextRoundSent) {
                                        nextRoundSent = true;
                                        sendNextRoundPacket();
                                        resultPage = 0;
                                    }
                                }
                            }
                            case 2 -> {
                                // 마작 탁자 종료하는 로직
                                // 나중에 게임 준비 화면 만들면 그 화면으로 돌아가게 만들 예정
                                this.onClose();
                                return true;
                            }
                            default -> {
                                RiichiMahjong.LOGGER.info("Wrong Result Page!");
                                // 마작 탁자 종료하는 로직 추가?
                                this.onClose();
                                return true;
                            }
                        }
                        return true;
                    }
                }
            }

            List<Tile> hand = this.menu.getPlayerHand();

            for (int i = 0; i < hand.size(); i++) {
                int tileX = handStartX + (i * sx(TILE_SPACING));

                boolean withinX = mouseX >= tileX && mouseX <= tileX + sx(TILE_WIDTH);
                boolean withinY = mouseY >= handStartY && mouseY <= handStartY + sx(TILE_HEIGHT);

                if (withinX && withinY) {
                    if (riichiMode) {
                        sendRiichiPacket(i);
                        riichiMode = false;
                    } else {
                        sendDiscardPacket(i);
                    }
                    return true;
                }
            }

            boolean buttonWithinX = mouseX >= buttonStartX - sx(2) && mouseX <= buttonStartX + makeUpTextWidth + sx(2);
            boolean buttonWithinY = mouseY >= buttonStartY - sx(2) && mouseY <= buttonStartY + sx(10);

            if (buttonWithinX && buttonWithinY) {
                sendHandMakeUpPacket(!this.menu.isHandMakeUp());
            }
        }

        // 망겜 초기화용
//        if(mouseX >= px(-10) && mouseX <= px(-5) && mouseY >= py(-10) && mouseY <= py(-5)){
//            ClientPlayNetworking.send(ModPackets.DEBUG_RESET_PACKET, PacketByteBufs.create());
//            return true;
//        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ResourceLocation getTileTexture(Tile tile){
        return new ResourceLocation("riichi-mahjong","textures/gui/tiles/" + tile.getTextureId() + ".png");
    }

    private ResourceLocation getNumberTexture(int number){
        return new ResourceLocation("riichi-mahjong","textures/gui/numbers/digit_" + number + ".png");
    }

    private ResourceLocation getPositiveNumberTexture(int number){
        return new ResourceLocation("riichi-mahjong","textures/gui/numbers/positive/positive_" + number + ".png");
    }

    private ResourceLocation getNegativeNumberTexture(int number){
        return new ResourceLocation("riichi-mahjong","textures/gui/numbers/negative/negative_" + number + ".png");
    }

    private void drawCallButton(GuiGraphics guiGraphics, int x, int y, String text, int mouseX, int mouseY){
        boolean hovered = isWithinCallButton(mouseX, mouseY, x, y);
        guiGraphics.fill(x,y,x+sx(CALL_BUTTON_WIDTH),y+sx(CALL_BUTTON_HEIGHT),hovered ? 0xFF888888 : 0xFF555555);
        int textWidth = textW(text);
        drawTextAt(guiGraphics,text,x+(sx(CALL_BUTTON_WIDTH)-textWidth)/2,y+sx(4),0xFFFFFF);
    }

    private boolean isWithinCallButton(double mouseX, double mouseY, int x, int y){
        return mouseX >= x && mouseX <= x + sx(CALL_BUTTON_WIDTH)
                && mouseY >= y && mouseY <= y + sx(CALL_BUTTON_HEIGHT);
    }

    private void updateLayout(){
        panelX = (this.width - this.imageWidth) / 2;
        panelY = (this.height - this.imageHeight) / 2;
        scale = this.imageWidth / (float) LOGICAL_W;

        tableW = Math.round(this.imageWidth * TABLE_SCALE);
        tableH = Math.round(this.imageHeight * TABLE_SCALE);
        tableX = panelX - (tableW - this.imageWidth) / 2;
        tableY = panelY - (tableH - this.imageHeight) / 2;
    }

    private int sx(int v){ return Math.round(v * scale); }      // 길이 변환
    private int px(int v){ return panelX + sx(v); }             // 논리 X -> 화면 X
    private int py(int v){ return panelY + sx(v); }             // 논리 Y -> 화면 Y

    private void drawTile(GuiGraphics g, Tile t, int x, int y, int w, int h){
        g.blit(getTileTexture(t),x,y,0,0,w,h,w,h);
    }

    // 폰트 확대는 pose 스케일로 처리
    private void drawText(GuiGraphics g, String s, int lx, int ly, int color){
        g.pose().pushPose();
        g.pose().translate(px(lx), py(ly), 0);
        g.pose().scale(scale,scale,1f);
        g.drawString(this.font,s,0,0,color);
        g.pose().popPose();
    }

    private void drawTextAt(GuiGraphics g,String s, int screenX, int screenY, int color){
        g.pose().pushPose();
        g.pose().translate(screenX, screenY, 0);
        g.pose().scale(scale,scale,1f);
        g.drawString(this.font,s,0,0,color);
        g.pose().popPose();
    }

    private int textW(String s){ return Math.round(this.font.width(s) * scale); }

    private boolean hasElapsed(long t){
        return this.menu.isGameEnd() && System.currentTimeMillis() - resultShownAt >= t;
    }

    private void drawDoraRow(GuiGraphics g, String label, List<Tile> indicators, int startX, int startY){
        if(indicators.isEmpty()) return;

        int labelWidth = textW(label);
        g.fill(startX - sx(2), startY - sx(17), startX + labelWidth + sx(2), startY - sx(4), 0x66FFFFFF);
        drawTextAt(g, label, startX, startY - sx(15), 0x000000);

        for(int i = 0; i < 5; i++){
            int x = startX + i * sx(TILE_WIDTH);
            ResourceLocation tex = i < indicators.size() ? getTileTexture(indicators.get(i)) : TILE_BACK;
            g.blit(tex, x, startY, 0, 0, sx(TILE_WIDTH), sx(TILE_HEIGHT), sx(TILE_WIDTH), sx(TILE_HEIGHT));
        }
    }

    private void drawDora(GuiGraphics g, int startX, int startY){
        drawDoraRow(g, "도라", this.menu.getDoraIndicatorPool(), startX, startY);
    }

    private void drawUraDora(GuiGraphics g, int startX, int startY){
        drawDoraRow(g, "뒷도라", this.menu.getUraDoraIndicatorPool(), startX + 5 * sx(TILE_WIDTH), startY);
    }

    private void renderWaitingPage(GuiGraphics guiGraphics){
        int centerX = px(128);
        int centerY = py(100);

        String gameTitle = "4인동풍전";
        int titleWidth = textW(gameTitle);
        int titleX = centerX - titleWidth / 2;
        int titleY = py(5);

        int infoSize = sx(10);
        int infoX = titleX + titleWidth + sx(8);
        int infoY = py(0) + (sx(18) - infoSize) / 2;
        int infoTextX = infoX + infoSize - sx(1);
        int infoTextY = titleY;
        int infoTextCount = 0;

        int seatPanelX, seatPanelY;
        int seatOffset = sx(48);
        int seatPanelW = sx(72), seatPanelH = sx(28);

        guiGraphics.fill(titleX - sx(5), titleY - sx(5), titleX + titleWidth + sx(5), titleY + sx(13),0xCC333333);
        drawTextAt(guiGraphics,gameTitle,titleX,titleY,0xCCCCCC);

        guiGraphics.blit(INFO_TEXTURE, infoX, infoY, 0, 0, infoSize, infoSize, infoSize, infoSize);

        int mySeat = this.menu.getViewSeat();
        for(int seat=0;seat<4;seat++) {
            switch ((seat - mySeat + 4 ) % 4) {
                case 1 -> {
                    seatPanelX = centerX + seatOffset;
                    seatPanelY = centerY - seatPanelH / 2;
                }
                case 2 -> {
                    seatPanelX = centerX - seatPanelW / 2;
                    seatPanelY = centerY - seatOffset - seatPanelH;
                }
                case 3 -> {
                    seatPanelX = centerX - seatOffset - seatPanelW;
                    seatPanelY = centerY - seatPanelH / 2;
                }
                default -> {
                    seatPanelX = centerX - seatPanelW / 2;
                    seatPanelY = centerY + seatOffset;

                }
            }

            guiGraphics.blit(SCORE_PANEL_TEXTURE, seatPanelX, seatPanelY, 0, 0, seatPanelW, seatPanelH, seatPanelW, seatPanelH);

            boolean seatIsEmpty = this.menu.getSeatUuid(seat) == null;

            int faceSize = sx(20);
            int faceX = seatPanelX + sx(5);
            int faceY = seatPanelY + (seatPanelH - faceSize) / 2;
            if(seatIsEmpty){
                guiGraphics.fill(faceX, faceY, faceX + faceSize, faceY + faceSize, 0xFF555866);
                int qW = textW("?");
                drawTextAt(guiGraphics,"?",faceX + (faceSize - qW) / 2, faceY + (faceSize - sx(8)) / 2, 0xFFFFFF);
            }else {
                PlayerFaceRenderer.draw(guiGraphics, getSeatSkin(seat), faceX, faceY, faceSize);
            }

            String name = seatIsEmpty ? "빈 자리" : getSeatName(seat);
            int nameWidth = textW(name);
            while(nameWidth > sx(42)){
                name = name.substring(0,name.length()-2);
                nameWidth = textW(name);
            }
            int nameX = seatPanelX + sx(26) + (sx(42) - nameWidth) / 2;
            int nameY = seatPanelY + sx(4);
            int nameColor = seatIsEmpty ? 0xAAAAAA : 0xFFFFFF;
            drawTextAt(guiGraphics,name,nameX,nameY,nameColor);
        }

        if(showInfo){
            List<String> ruleList = new ArrayList<>();
            int ruleBoxWidth = 0;
            ruleList.add("시작 점수: 25000점");
            ruleList.add("적도라: 3장");
            ruleList.add("하코시타: 사용");
            ruleList.add("구종구패: 있음");
            ruleList.add("유국만관: 있음");
            ruleList.add("텐파이료: 총 3000점");
            for(String rule : ruleList){
                int ruleWidth = textW(rule);
                if(ruleWidth > ruleBoxWidth) ruleBoxWidth = ruleWidth;
            }
            guiGraphics.fill(infoX + infoSize / 2, titleY - sx(5), infoX + infoSize / 2 + ruleBoxWidth + sx(8), titleY + sx(63),0xFFCCCCCC);
            guiGraphics.fill(infoX + infoSize / 2 + sx(2), titleY - sx(3), infoX + infoSize / 2 + ruleBoxWidth + sx(6), titleY + sx(61), 0xFF33478A);
            for(String rule : ruleList){
                drawTextAt(guiGraphics,rule,infoTextX,infoTextY,0xCCCCCC);
                infoTextCount++;
                infoTextY = titleY + infoTextCount * sx(10);
            }
        }

        if(this.menu.getMySeat() == 0) {
            String startMahjongText = "시작";
            int startMahjongWidth = textW(startMahjongText);
            int startMahjongButtonX = px(128) - startMahjongWidth / 2;
            int startMahjongButtonY = py(190);

            guiGraphics.fill(startMahjongButtonX - sx(5), startMahjongButtonY - sx(4), startMahjongButtonX + startMahjongWidth + sx(5), startMahjongButtonY + sx(12), 0xCC000000);
            drawTextAt(guiGraphics, startMahjongText, startMahjongButtonX, startMahjongButtonY, 0xFFFFFF);
        }
    }

    private void renderMahjongPage(GuiGraphics guiGraphics, int mouseX, int mouseY, List<Tile> hand){
        if(!this.menu.canRiichi()) riichiMode = false;
        if(!this.menu.isGameEnd()) nextRoundSent = false;
        if(this.menu.isGameEnd()){
            if(!wasGameEnd){
                resultShownAt = System.currentTimeMillis();
                wasGameEnd = true;
            }
        }else{
            wasGameEnd = false;
        }

        handStartX = px(20);
        handStartY = py(185);

        for(int i = 0; i < hand.size(); i++){
            Tile tile = hand.get(i);
            int x = handStartX + (i * sx(TILE_SPACING));

            boolean hovered = mouseX >= x && mouseX <= x + sx(TILE_WIDTH)
                    && mouseY >= handStartY && mouseY <= handStartY + sx(TILE_HEIGHT);

            if(hovered && !this.menu.isGameEnd()){
                guiGraphics.fill(x-sx(1), handStartY-sx(3), x+sx(TILE_WIDTH)+sx(1), handStartY-sx(1), 0xFFFFFFFF);
                guiGraphics.blit(getTileTexture(tile), x, handStartY-sx(3), 0, 0, sx(TILE_WIDTH), sx(TILE_HEIGHT), sx(TILE_WIDTH), sx(TILE_HEIGHT));
            } else {
                guiGraphics.blit(getTileTexture(tile), x, handStartY, 0, 0, sx(TILE_WIDTH), sx(TILE_HEIGHT), sx(TILE_WIDTH), sx(TILE_HEIGHT));
            }
        }

        List<List<Tile>> melds = this.menu.getMelds();
        List<MeldType> meldTypes = this.menu.getMeldTypes();    // 왜 아직 안 쓰는 지 모르겠다

        if(!melds.isEmpty()){
            int meldStartX = handStartX + sx(hand.size() * TILE_SPACING + 10);
            int meldGroupGap = sx(6);
            int meldX = meldStartX;

            for(int m = 0; m < melds.size(); m++){
                List<Tile> meld = melds.get(m);
                for(int i = 0; i < meld.size(); i++){
                    Tile tile = meld.get(i);
                    drawTile(guiGraphics,tile,meldX,handStartY,sx(TILE_WIDTH),sx(TILE_HEIGHT));
                    meldX += sx(TILE_SPACING);
                }
                meldX += meldGroupGap;
            }

            String meldText = "부른패";
            int meldTextWidth = textW(meldText);
            guiGraphics.fill(meldStartX-sx(2), handStartY-sx(17), meldStartX+meldTextWidth+sx(2), handStartY-sx(4), 0x66FFFFFF);
            drawTextAt(guiGraphics,meldText,meldStartX,handStartY-sx(15),0x000000);
        }

        List<List<Tile>> allDiscarded = this.menu.getAllDiscarded();
        int centerX = px(128);
        int centerY = py(100);
        int discardOffset = sx(37);     // 테이블 중심에서 각 버림패 줄까지의 거리.
        int discardTilePerRow = 6;
        int scoreOffset = sx(20);
        int riichiOffset = sx(30);

        String round;
        switch(this.menu.getRoundWind()){
            case 0 -> round = "동";
            case 1 -> round = "남";
            case 2 -> round = "서";
            case 3 -> round = "북";
            default -> round = "??";
        }
        round += (this.menu.getDealerSeat()+1) + "국";
        drawText(guiGraphics,round,128-(this.font.width(round)/2),100-10,0x666666);

        String wallSize = this.menu.getWallSize() + "";
        drawText(guiGraphics,wallSize,128-this.font.width(wallSize)/2,100+3,0x666666);

        for(int seat=0;seat<allDiscarded.size();seat++){
            List<Tile> discardedBySeat = allDiscarded.get(seat);

            int numberCount = 0;
            int scoreTemp = this.menu.getScoreBySeat()[seat];
            int discardTileWidth = sx(TILE_WIDTH) * 8 / 9;
            int discardTileHeight = sx(TILE_HEIGHT) * 8 / 9;
            int scoreTextureWidth = sx(SCORE_TEXTURE_WIDTH);
            int scoreTextureHeight = sx(SCORE_TEXTURE_HEIGHT);
            int riichiTextureWidth = sx(32);
            int riichiTextureHeight = sx(5);
            boolean isMinus = scoreTemp < 0;

            int baseX = centerX - (discardTilePerRow * discardTileWidth) / 2;
            int baseY = centerY + discardOffset;
            do{
                scoreTemp /= 10;
                numberCount++;
            }while(scoreTemp != 0);
            int scoreX = centerX - (numberCount * scoreTextureWidth) / 2;
            if(isMinus) scoreX -= scoreTextureWidth / 2;
            int scoreY = centerY + scoreOffset;
            int riichiX = centerX - riichiTextureWidth / 2;
            int riichiY = centerY + riichiOffset;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY, 0);
            guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotations[seat]));
            guiGraphics.pose().translate(-centerX, -centerY, 0);

            for(int i=0;i<discardedBySeat.size();i++){
                Tile tile = discardedBySeat.get(i);
                int row = i / discardTilePerRow;
                int col = i % discardTilePerRow;

                int x = baseX + col * discardTileWidth;
                int y = baseY + row * discardTileHeight;
                guiGraphics.blit(getTileTexture(tile),x,y,0,0,discardTileWidth,discardTileHeight,discardTileWidth,discardTileHeight);
            }

            scoreTemp = this.menu.getScoreBySeat()[seat];
            do{
                int number = scoreTemp % 10 * (scoreTemp % 10 < 0 ? -1 : 1);
                scoreTemp /= 10;
                numberCount--;

                int x = scoreX + scoreTextureWidth * numberCount + (isMinus ? textW("-") : 0);   // textW("-") 랑 scoreTextureWidth 중에 더 자연스러운 것으로 바꾸기
                int y = scoreY;
                guiGraphics.blit(getNumberTexture(number),x,y,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);
            }while(scoreTemp != 0);
            if(isMinus) guiGraphics.blit(MINUS_DIGIT_TEXTURE,scoreX,scoreY,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);

            guiGraphics.pose().popPose();
            guiGraphics.pose().pushPose();

            boolean[] isRiichi = this.menu.isRiichi();
            if(isRiichi[seat]) guiGraphics.blit(new ResourceLocation("riichi-mahjong","textures/gui/riichi_stick.png"),
                    riichiX,riichiY,0,0,riichiTextureWidth,riichiTextureHeight,riichiTextureWidth,riichiTextureHeight);

            guiGraphics.pose().popPose();
        }

        String handText = "내 손패";
        int handTextWidth = textW(handText);
        guiGraphics.fill(handStartX-sx(2), handStartY-sx(17), handStartX+handTextWidth+sx(2), handStartY-sx(4), 0x66FFFFFF);
        drawTextAt(guiGraphics,handText,handStartX,handStartY-sx(15),0x000000);

        int startX = (this.width - this.imageWidth) / 2;
        int lineY = (handStartY + py(140)) / 2;
        guiGraphics.fill(startX, lineY, startX+this.imageWidth, lineY+1, 0x66FFFFFF);

        buttonStartX = px(-15);
        buttonStartY = py(80);

        if(this.menu.isFuriten()){
            String furitenString = "후리텐";
            int furitenWidth = textW(furitenString);
            guiGraphics.fill(handStartX-furitenWidth-sx(7),handStartY+sx(3),handStartX-sx(3),handStartY+sx(15),0x66FF8855);
            drawTextAt(guiGraphics,furitenString,handStartX-furitenWidth-sx(5),handStartY+sx(5),0xFF5555);
        }

        String makeUpText = "정리";
        makeUpTextWidth = textW(makeUpText);

        int makeUpButtonColor = menu.isHandMakeUp() ? 0xFF48E317 : 0xFF999999;

        guiGraphics.fill(buttonStartX-sx(3), buttonStartY-sx(3), buttonStartX+makeUpTextWidth+sx(3), buttonStartY+sx(11), 0xFF9C5856);
        guiGraphics.fill(buttonStartX-sx(2), buttonStartY-sx(2), buttonStartX+makeUpTextWidth+sx(2), buttonStartY+sx(10), makeUpButtonColor);
        drawText(guiGraphics,makeUpText,-15,80,0x111111);

        String turnOrderText = "턴 : " + this.menu.getTurnOrder();
        int turnStartX = px(230);
        int turnStartY = py(10);
        int turnTextWidth = textW(turnOrderText);

        guiGraphics.fill(turnStartX-sx(2),turnStartY-sx(2),turnStartX+turnTextWidth+sx(2),turnStartY+sx(12), 0x66FFFFFF);
        drawText(guiGraphics,turnOrderText,230,10,0x000000);

        boolean hasCallOption = this.menu.canDeclareKyuushu() || this.menu.canPon() || this.menu.canChi()
                || this.menu.canDaiminkan() || this.menu.canAnkan() || this.menu.canKakan()
                || this.menu.canRiichi() || this.menu.canTsumo() || this.menu.canRon();

        if(hasCallOption && !this.menu.isGameEnd()){
            callButtonStartX = px(100);
            callButtonStartY = py(190);

            int x = callButtonStartX;

            if(riichiMode) {
                drawCallButton(guiGraphics, x, callButtonStartY, "취소", mouseX, mouseY);
            }else {
                if (this.menu.canDeclareKyuushu()) {
                    drawCallButton(guiGraphics, x, callButtonStartY, "유국", mouseX, mouseY);
                    x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                }
                if (this.menu.canPon()) {
                    drawCallButton(guiGraphics, x, callButtonStartY, "퐁", mouseX, mouseY);
                    x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                }
                if (this.menu.canChi()) {
                    drawCallButton(guiGraphics, x, callButtonStartY, "치", mouseX, mouseY);
                    x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                }
                if (this.menu.canDaiminkan() || this.menu.canAnkan() || this.menu.canKakan()) {
                    drawCallButton(guiGraphics, x, callButtonStartY, "깡", mouseX, mouseY);
                    x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                }
                if (this.menu.canRiichi()) {
                    if (!riichiMode) {
                        drawCallButton(guiGraphics, x, callButtonStartY, "리치", mouseX, mouseY);
                    }
                    x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                }
                if (this.menu.canTsumo()) {
                    drawCallButton(guiGraphics, x, callButtonStartY, "쯔모", mouseX, mouseY);
                    x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                }
                if (this.menu.canRon()) {
                    drawCallButton(guiGraphics, x, callButtonStartY, "론", mouseX, mouseY);
                    x += sx(CALL_BUTTON_WIDTH + CALL_BUTTON_GAP);
                }

                drawCallButton(guiGraphics, x, callButtonStartY, "패스", mouseX, mouseY);
            }
        }

        if(popupType != PopupType.NONE){
            int popupX = px(88);
            int popupY = py(70);

            List<String> labels = new ArrayList<>();
            if(popupType == PopupType.CHI){
                for(List<Integer> option : this.menu.getChiOptions()){
                    Tile tile1 = this.menu.getPlayerHand().get(option.get(0));
                    Tile tile2 = this.menu.getPlayerHand().get(option.get(1));
                    labels.add(tile1.getDisplayName() + " - " + tile2.getDisplayName());
                }
            }else{
                for(Tile tile : kanPopupTiles){
                    labels.add(tile.getDisplayName());
                }
            }

            guiGraphics.fill(popupX-sx(4), popupY-sx(4), popupX+sx(84), popupY + sx(labels.size()*18+4), 0xCC000000);

            for(int i=0;i<labels.size();i++){
                int optionY = py(70 + i * 18);
                boolean hovered = mouseX >= popupX && mouseX <= popupX+sx(80)
                        && mouseY >= optionY && mouseY <= optionY+sx(16);
                guiGraphics.fill(popupX, optionY, popupX+sx(80), optionY+sx(16), hovered ? 0xFF888888 : 0xFF555555);
                drawTextAt(guiGraphics,labels.get(i),popupX+sx(4), optionY+sx(4), 0xFFFFFF);
            }
        }

        drawDora(guiGraphics,px(-8),py(16));
    }

    private void renderResultPage(GuiGraphics guiGraphics, List<Tile> hand){
        String winText;
        if(this.menu.isWin()){
            winText = this.menu.isTsumo() ? "쯔모" : "론";
        }else{
            String reason = this.menu.getDrawReason();
            winText = "유국! " + (reason != null && !reason.isEmpty() ? reason : "알 수 없는 이유") + "!";
        }
        int textColor = this.menu.isWin() ? 0xFFD700 : 0xAAAAAA;

        int textWidth = textW(winText);
        int endCenterX = this.menu.isWin() ? px(230) : px(128) - textWidth / 2;
        int endCenterY = this.menu.isWin() ? py(18) : py(100);

        guiGraphics.fill(tableX, tableY, tableX + tableW, tableY + tableH, 0xCC000000);

        if(this.menu.isWin()) {
            guiGraphics.fill(panelX, panelY, panelX + this.imageWidth, panelY + this.imageHeight, 0xFFD4A93C);  // 0xFF6A6E76 회색    0xFF5C7899 남색
            guiGraphics.fill(panelX + sx(3), panelY + sx(3), panelX + this.imageWidth - sx(3), panelY + this.imageHeight - sx(3), 0xFF143D2B);  // 0xFF262A30 회색    // 0xFF1A2438 남색
        }

        if(!this.menu.isWin()){
            guiGraphics.fill(endCenterX-sx(6),endCenterY-sx(6), endCenterX+textWidth+sx(6), endCenterY+sx(10), 0xCC8E403A);
        }
        drawTextAt(guiGraphics, winText, endCenterX, endCenterY, textColor);

        if(this.menu.isWin()) {
            List<Tile> winningHand = new ArrayList<>(hand);

            int winningHandX = px(128) - (sx(TILE_WIDTH) * hand.size() + sx(5)) / 2;
            int winningHandY = py(15);

            Tile winningTile = this.menu.getWinningTile();
            Tile separated = null;
            if (winningTile != null) {
                for (int i = winningHand.size() - 1; i >= 0; i--) {
                    Tile t = winningHand.get(i);
                    if (t.equals(winningTile) && t.isRed() == winningTile.isRed()) {
                        separated = winningHand.remove(i);
                        break;
                    }
                }
            }

            for(int i=0;i<winningHand.size();i++){
                Tile t = winningHand.get(i);
                guiGraphics.blit(getTileTexture(t),winningHandX + sx(TILE_WIDTH) * i, winningHandY, 0, 0, sx(TILE_WIDTH),sx(TILE_HEIGHT),sx(TILE_WIDTH),sx(TILE_HEIGHT));
            }
            if(separated != null) {
                guiGraphics.blit(getTileTexture(separated), winningHandX + sx(TILE_WIDTH) * hand.size() + sx(5), winningHandY, 0, 0, sx(TILE_WIDTH), sx(TILE_HEIGHT), sx(TILE_WIDTH), sx(TILE_HEIGHT));
            }

            drawDora(guiGraphics,px(80),py(50));
            drawUraDora(guiGraphics,px(86),py(50));

            LocalPlayer player = Minecraft.getInstance().player;
            if(player != null){
                // 지금 실제 자세 저장
                float prevYBodyRot = player.yBodyRot;
                float prevYRot = player.getYRot();
                float prevXRot = player.getXRot();
                float prevYHeadRot = player.yHeadRot;

                // 그릴 때 고정값으로 덮어쓰기
                player.yBodyRot = 180f;
                player.setYRot(180f);
                player.setXRot(0f);
                player.yHeadRot = 180f;

                int charX = px(32);
                int charY = py(125);
                int charScale = sx(30);

                Quaternionf bodyRotation = new Quaternionf()
                        .rotateZ((float) Math.toRadians(180))
                        .rotateY((float) Math.toRadians(20));

                InventoryScreen.renderEntityInInventory(
                        guiGraphics, charX, charY, charScale, bodyRotation, null, player
                );

                // 그린 후 원래 자세로 복구
                player.yBodyRot = prevYBodyRot;
                player.setYRot(prevYRot);
                player.setXRot(prevXRot);
                player.yHeadRot = prevYHeadRot;
            }
        }

        if(this.menu.isWin() && !this.menu.getYaku().isEmpty()){
            int yakuCount = 0;
            for(String yaku : this.menu.getYaku()){
                int yakuCX = px(170 - 80 + 85 * (yakuCount / 6));
                int yakuCY = py(74 + 17 * (yakuCount % 6));

                drawTextAt(guiGraphics,yaku,yakuCX,yakuCY,0xFFFFFF);

                yakuCount++;
            }
        }

        if(this.menu.isWin() && !this.menu.getHan().isEmpty()){
            int hanCount = 0;
            for(int han : this.menu.getHan()){
                int hanX = px(170 - 40 + 85 * (hanCount / 6));
                int hanY = py(74 + 17 * (hanCount % 6));
                String hanText = han + "판";

                drawTextAt(guiGraphics,hanText,hanX,hanY,0xFFFFFF);

                hanCount++;
            }
        }

        confirmText = "다음";
        confirmWidth = textW(confirmText);
        confirmButtonX = panelX + sx(244) - confirmWidth;
        confirmButtonY = py(180);

        drawConfirmButton(guiGraphics, resultPage);

        if(this.menu.isWin()) {
            int han = this.menu.getWinHan();
            int fu = this.menu.getWinFu();
            int score = this.menu.getWinScores();
            String label = this.menu.getWinLabel();
            boolean isYakuman = label.equals("역만") || label.equals("더블역만") || label.endsWith("배 역만");
            String line1 = isYakuman ? ""
                    : han + "판 " + fu + "부";

            int gap = sx(6);
            int hanFuWidth = sx(this.font.width("99판 110부"));
            int scoreWidth = sx(this.font.width("192000"));

            int scoreX = confirmButtonX - gap - gap - sx(this.font.width(Integer.toString(score)));
            int hanFuX = confirmButtonX - gap - gap - scoreWidth - gap - hanFuWidth;
            int labelX = hanFuX - gap - sx(this.font.width(label));

            drawTextAt(guiGraphics, label, labelX, confirmButtonY, 0xFFD700);
            drawTextAt(guiGraphics, line1, hanFuX, confirmButtonY, 0xFFD700);
            drawTextAt(guiGraphics, Integer.toString(score), scoreX, confirmButtonY, 0xFFD700);
        }
    }

    private void renderSettleScorePage(GuiGraphics guiGraphics){
        guiGraphics.fill(tableX, tableY, tableX + tableW, tableY + tableH, 0xCC000000);

        int scoreOffset = sx(48);
        int scorePanelX, scorePanelY, scoreX, scoreY, settleScoreX, settleScoreY;
        int scorePanelW = sx(72), scorePanelH = sx(28);
        int[] scoreBySeat = this.menu.getScoreBySeat();
        int[] settleScoreBySeat = this.menu.getSettleScoreBySeat();
        int scoreTemp, settleScoreTemp;
        int scoreCount, settleScoreCount;
        boolean isScoreMinus, isSettleScoreMinus;

        for(int seat=0;seat<4;seat++){
            scoreTemp = scoreBySeat[seat];
            settleScoreTemp = settleScoreBySeat[seat];
            scoreCount = 0;
            settleScoreCount = 0;
            isScoreMinus = scoreTemp < 0;
            isSettleScoreMinus = settleScoreTemp < 0;

            if(!hasElapsed(SCORE_APPLY_DELAY)){
                scoreTemp -= settleScoreTemp;
                isScoreMinus = scoreTemp < 0;
            }else{
                settleScoreTemp = 0;
            }

            switch(seat){
                case 1 ->{
                    scorePanelX = px(128) + scoreOffset;
                    scorePanelY = py(100) - scorePanelH / 2;
                }
                case 2 ->{
                    scorePanelX = px(128) - scorePanelW / 2;
                    scorePanelY = py(100) - scoreOffset - scorePanelH;
                }
                case 3 ->{
                    scorePanelX = px(128) - scoreOffset - scorePanelW;
                    scorePanelY = py(100) - scorePanelH / 2;
                }
                default ->{
                    scorePanelX = px(128) - scorePanelW / 2;
                    scorePanelY = py(100) + scoreOffset;
                }
            }

            guiGraphics.blit(SCORE_PANEL_TEXTURE, scorePanelX, scorePanelY,0, 0, scorePanelW, scorePanelH, scorePanelW, scorePanelH);

            do{
                scoreTemp /= 10;
                scoreCount++;
            }while(scoreTemp != 0);

            do{
                settleScoreTemp /= 10;
                settleScoreCount++;
            }while(settleScoreTemp != 0);

            int scoreTextureWidth = sx(SCORE_TEXTURE_WIDTH);
            int scoreTextureHeight = sx(SCORE_TEXTURE_HEIGHT);

            int faceSize = sx(20);
            int faceX = scorePanelX + sx(5);
            int faceY = scorePanelY + (scorePanelH - faceSize) / 2;
            PlayerFaceRenderer.draw(guiGraphics,getSeatSkin(seat),faceX,faceY,faceSize);

            String name = getSeatName(seat);
            int nameWidth = textW(name);
            while(nameWidth > sx(42)){
                name = name.substring(0,name.length()-2);
                nameWidth = textW(name);
            }
            int nameX = scorePanelX + sx(26) + (sx(42) - nameWidth) / 2;
            int nameY = scorePanelY + sx(4);
            drawTextAt(guiGraphics,name,nameX,nameY,0xFFFFFF);

            scoreTemp = scoreBySeat[seat];
            settleScoreTemp = settleScoreBySeat[seat];

            if(!hasElapsed(SCORE_APPLY_DELAY)){
                scoreTemp -= settleScoreTemp;
            }else{
                settleScoreTemp = 0;
            }

            scoreX = scorePanelX + sx(26) + (sx(42) - scoreCount * scoreTextureWidth) / 2;
            if(isScoreMinus) scoreX -= scoreTextureWidth / 2;
            scoreY = scorePanelY + sx(14) + (sx(14) - scoreTextureHeight) / 2;
            settleScoreX = scorePanelX + (scorePanelW - settleScoreCount * scoreTextureWidth) / 2;
            if(isSettleScoreMinus) settleScoreX -= scoreTextureWidth / 2;
            settleScoreY = scorePanelY - sx(10);

            do{
                int number = scoreTemp % 10 * (scoreTemp % 10 < 0 ? -1 : 1);
                scoreTemp /= 10;
                scoreCount--;

                int x = scoreX + scoreTextureWidth * (scoreCount + (isScoreMinus ? 1 : 0));
                int y = scoreY;
                guiGraphics.blit(getNumberTexture(number),x,y,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);
            }while(scoreTemp != 0);

            if(isScoreMinus) guiGraphics.blit(MINUS_DIGIT_TEXTURE,scoreX,scoreY,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);

            do{
                int number = settleScoreTemp % 10 * (settleScoreTemp % 10 < 0 ? -1 : 1);
                settleScoreTemp /= 10;
                settleScoreCount--;

                int x = settleScoreX + scoreTextureWidth * (settleScoreCount + 1);
                int y = settleScoreY;
                guiGraphics.blit(!isSettleScoreMinus ? getPositiveNumberTexture(number) : getNegativeNumberTexture(number)
                        ,x,y,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);
            }while(settleScoreTemp != 0);

            guiGraphics.blit(!isSettleScoreMinus ? POSITIVE_DIGIT_TEXTURE : NEGATIVE_DIGIT_TEXTURE
                    ,settleScoreX,settleScoreY,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);
        }

        confirmText = "확인";
        confirmWidth = textW(confirmText);
        confirmButtonX = panelX + sx(244) - confirmWidth;
        confirmButtonY = py(180);

        drawConfirmButton(guiGraphics, resultPage);
    }

    private void renderMatchOverPage(GuiGraphics guiGraphics){
        guiGraphics.fill(tableX, tableY, tableX + tableW, tableY + tableH, 0xCC000000);

        int faceSize = sx(20);

        int[] scoreBySeat = this.menu.getScoreBySeat();
        int scoreTemp, scoreCount = 0;
        boolean isScoreMinus;

        int scoreTextureWidth = sx(SCORE_TEXTURE_WIDTH);
        int scoreTextureHeight = sx(SCORE_TEXTURE_HEIGHT);

        Integer[] order = {0, 1, 2, 3};
        int[] s = this.menu.getScoreBySeat();
        Arrays.sort(order, (a, b) -> Integer.compare(s[b], s[a]));   // 안정 정렬이라 동점은 기존 좌석 순서 유지

        for(int rank = 0;rank < 4;rank++){
            int seat = order[rank];

            int rankX = px(128);
            int rankY = py(45 + 30 * rank) + (sx(20) - scoreTextureHeight * 2) / 2;

            int faceX = px(145);
            int faceY = py(45 + 30 * rank);

            int nameX = px(170);
            int nameY = py(45 + 30 * rank);

            int scoreResultX = px(170);
            int scoreResultY = py(55 + 30 * rank);

            guiGraphics.blit(getNumberTexture(rank+1),rankX,rankY,0,0,scoreTextureWidth*2,scoreTextureHeight*2,scoreTextureWidth*2,scoreTextureHeight*2);
            PlayerFaceRenderer.draw(guiGraphics,getSeatSkin(seat),faceX,faceY,faceSize);

            String name = getSeatName(seat);
            drawTextAt(guiGraphics,name,nameX,nameY,0xFFFFFF);

            scoreTemp = scoreBySeat[seat];
            isScoreMinus = scoreTemp < 0;

            do{
                scoreTemp /= 10;
                scoreCount++;
            }while(scoreTemp != 0);

            scoreTemp = scoreBySeat[seat];

            do{
                int number = scoreTemp % 10 * (scoreTemp % 10 < 0 ? -1 : 1);
                scoreTemp /= 10;
                scoreCount--;

                int x = scoreResultX + scoreTextureWidth * (scoreCount + (isScoreMinus ? 1 : 0));
                int y = scoreResultY;
                guiGraphics.blit(getNumberTexture(number),x,y,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);
            }while(scoreTemp != 0);

            if(isScoreMinus) guiGraphics.blit(MINUS_DIGIT_TEXTURE,scoreResultX,scoreResultY,0,0,scoreTextureWidth,scoreTextureHeight,scoreTextureWidth,scoreTextureHeight);
        }

        confirmText = "확인";
        confirmWidth = textW(confirmText);
        confirmButtonX = panelX + sx(244) - confirmWidth;
        confirmButtonY = py(180);

        drawConfirmButton(guiGraphics, resultPage);
    }

    private void drawConfirmButton(GuiGraphics guiGraphics, int resultPage){
        long delay;
        switch(resultPage){
            case 0 -> delay = NEXT_BUTTON_DELAY;
            case 1 -> delay = CONFIRM_BUTTON_DELAY;
            case 2 -> delay = MATCH_OVER_DELAY;
            default -> delay = 10000000;
        }

        if(hasElapsed(delay)) {
            guiGraphics.fill(confirmButtonX - sx(5), confirmButtonY - sx(4), confirmButtonX + confirmWidth + sx(5), confirmButtonY + sx(12), 0xCC000000);
            drawTextAt(guiGraphics, confirmText, confirmButtonX, confirmButtonY, nextRoundSent ? 0x888888 : 0xFFFFFF);
        }
    }

    private PlayerInfo getSeatInfo(int seat){
        UUID uuid = this.menu.getSeatUuid(seat);
        if(uuid == null || this.minecraft.getConnection() == null) return null;
        return this.minecraft.getConnection().getPlayerInfo(uuid);
    }

    private String getSeatName(int seat){
        PlayerInfo info = getSeatInfo(seat);
        if(info != null) return info.getProfile().getName();
        if(seat == this.menu.getMySeat()) return this.minecraft.player.getGameProfile().getName();
        return DUMMY_NAMES[seat];
    }

    private ResourceLocation getSeatSkin(int seat){
        PlayerInfo info = getSeatInfo(seat);
        if(info != null) return info.getSkinLocation();
        if(seat == this.menu.getMySeat()) return this.minecraft.player.getSkinTextureLocation();
        return DefaultPlayerSkin.getDefaultSkin(DUMMY_UUIDS[seat]);
    }

    private void sendDiscardPacket(int index){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        buf.writeInt(index);
        ClientPlayNetworking.send(ModPackets.DISCARD_TILE_PACKET,buf);
    }

    private void sendRiichiPacket(int index){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        buf.writeInt(index);
        ClientPlayNetworking.send(ModPackets.DECLARE_RIICHI_PACKET,buf);
    }

    private void sendHandMakeUpPacket(boolean handMakeUp){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        buf.writeBoolean(handMakeUp);
        ClientPlayNetworking.send(ModPackets.HAND_MAKE_UP,buf);
    }

    private void sendKyuushuPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.DECLARE_KYUUSHU_PACKET, buf);
    }

    private void sendPonPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.DECLARE_PON_PACKET, buf);
    }

    private void sendChiPacket(int idx1, int idx2){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        buf.writeInt(idx1);
        buf.writeInt(idx2);
        ClientPlayNetworking.send(ModPackets.DECLARE_CHI_PACKET, buf);
    }

    private void sendDaiminkanPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.DECLARE_DAIMINKAN_PACKET, buf);
    }

    private void sendAnkanPacket(Tile tile){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        tile.writeToBuf(buf);
        ClientPlayNetworking.send(ModPackets.DECLARE_ANKAN_PACKET, buf);
    }

    private void sendKakanPacket(Tile tile){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        tile.writeToBuf(buf);
        ClientPlayNetworking.send(ModPackets.DECLARE_KAKAN_PACKET, buf);
    }

    private void sendTsumoPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.DECLARE_TSUMO_PACKET, buf);
    }

    private void sendRonPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.DECLARE_RON_PACKET, buf);
    }

    private void sendPassPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.PASS_CALL_PACKET, buf);
    }

    private void sendNextRoundPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.NEXT_ROUND_PACKET, buf);
    }

    private void sendMatchStartPacket(){
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(this.menu.getTablePos());
        ClientPlayNetworking.send(ModPackets.MATCH_START_PACKET, buf);
    }
}