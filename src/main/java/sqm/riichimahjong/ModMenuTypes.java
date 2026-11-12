package sqm.riichimahjong;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public class ModMenuTypes {
    public static final MenuType<MahjongMenu> MAHJONG_MENU = registerMenuType();

    private static MenuType<MahjongMenu> registerMenuType() {
        return Registry.register(
                BuiltInRegistries.MENU,
                RiichiMahjong.id("mahjong_menu"),
                new ExtendedScreenHandlerType<>(MahjongMenu::new)
        );
    }

    public static void registerModMenuTypes() {
        RiichiMahjong.LOGGER.info("Registering menu types for " + RiichiMahjong.MOD_ID);
    }
}