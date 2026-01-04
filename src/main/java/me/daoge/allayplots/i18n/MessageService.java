package me.daoge.allayplots.i18n;

import me.daoge.allayplots.config.PluginConfig;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.message.I18n;
import org.allaymc.api.message.LangCode;
import org.allaymc.api.utils.TextFormat;
import org.allaymc.papi.PlaceholderAPI;

public final class MessageService {
    private final PluginConfig config;
    private final PlaceholderAPI placeholderApi;

    public MessageService(PluginConfig config) {
        this.config = config;
        this.placeholderApi = PlaceholderAPI.getAPI();
    }

    public String render(EntityPlayer player, String template, Object... args) {
        return render(player, true, template, args);
    }

    public String renderInline(EntityPlayer player, String template, Object... args) {
        return render(player, false, template, args);
    }

    private String render(EntityPlayer player, boolean includePrefix, String template, Object... args) {
        String message = translate(player, template, args);
        if (includePrefix) {
            message = translate(player, config.messages().prefix()) + message;
        }
        message = applyPlaceholders(player, message);
        return TextFormat.colorize(message);
    }

    private String applyPlaceholders(EntityPlayer player, String text) {
        if (player == null || text == null) {
            return text;
        }
        return placeholderApi.setPlaceholders(player, text);
    }

    private String translate(EntityPlayer player, String template, Object... args) {
        if (template == null) {
            return "";
        }
        LangCode lang = I18n.get().getDefaultLangCode();
        if (player != null && player.getController() != null) {
            lang = player.getController().getLoginData().getLangCode();
        }
        return I18n.get().tr(lang, template, args);
    }
}
