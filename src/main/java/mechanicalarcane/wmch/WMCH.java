/*
 * Planning:
 * Regex GUI needs toMatch, toReplace, '&' char for color formatting,
 * Small explanation, credit to Vazkii, save, delete, preview i/o
 */

package mechanicalarcane.wmch;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import mechanicalarcane.wmch.config.Config;
import mechanicalarcane.wmch.util.ChatLog;
import mechanicalarcane.wmch.util.CopyMessageCommand;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flags;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageMetadata;

public class WMCH implements ClientModInitializer {
	public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("Where's My Chat History");
	public static final FabricLoader FABRICLOADER = FabricLoader.getInstance();

	public static Config config = Config.newConfig(false);
	/** Contains the sender and timestamp data of the last received chat message. */
	public static MessageMetadata lastMsgData = Util.NIL_METADATA;
	private static String lastWorld = null;


	/**
	 * Initializes MixinExtras for more Mixin annotations.
	 * Validates the newly created Config object.
	 * Initializes the ChatLog.
	 * Registers the CopyMessageCommand on client's server initialization.
	 * Registers a callback to {@link WMCH#writeCachedData(boolean)} with false
	 * to save data on normal game exits.
	 * Registers a callback to World Join events which loads cached data and
	 * adds boundary lines.
	 */
	@Override
	public void onInitializeClient() {
		MixinExtrasBootstrap.init();
		//CrowdinTranslate.downloadTranslations("wmch"); // use github workflow thing

		ChatLog.initialize();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> CopyMessageCommand.register(dispatcher) );

		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> WMCH.writeCachedData(false));
		// registers the cached message file importer and boundary sender
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {

			// Loads the chat log if SAVE_CHAT enabled and the ChatLog hasn't been loaded yet
			if( config.saveChat && !ChatLog.loaded ) {
				ChatLog.deserialize();
				ChatLog.restore(client);
			}


			String current = Util.currentWorldName(client);
			// continues if the boundary line is enabled, >0 messages sent, and if the last and current worlds were servers, that they aren't the same
			if( config.boundary && !Util.chatHud(client).getMessages().isEmpty() && (!current.startsWith("S_") || !lastWorld.startsWith("S_") || !current.equals(lastWorld)) ) {

				try {
					String levelName = (lastWorld = current).substring(2); // makes a variable to update lastWorld in a cleaner way

					Flags.BOUNDARY_LINE.set();
					client.inGameHud.getChatHud().addMessage( config.getFormattedBoundary(levelName) );
					Flags.BOUNDARY_LINE.remove();

				} catch(Exception e) {
					LOGGER.warn("[WMCH.boundary] An error occurred while adding the boundary line:", e);
				}
			}

			// sets all messages (restored and boundary line) to a addedTime of 0 to prevent instant rendering (#42)
			if(ChatLog.loaded && Flags.INIT.isSet())
				Util.chatHud(client).getVisibleMessages().replaceAll( ln -> new ChatHudLine.Visible(0, ln.content(), ln.indicator(), ln.endOfEntry()) );

			Flags.INIT.remove();
		});

		LOGGER.info("[WMCH()] Finished setting up!");
	}


	/**
	 * The callback method which saves cached message data.
	 * Injected into {@link MinecraftClient#run} to ensure
	 * saving whenever (reasonably) possible.
	 * @param crashed {@code true} if a crash occurred
	*/
	public static void writeCachedData(boolean crashed) {
		try {
			ChatLog.serialize(crashed);
		} catch(Exception e) {
			LOGGER.warn("[WMCH.writeCachedData({})] An error occurred while trying to save the chat log{}:", crashed, crashed ? " after a crash" : "", e);
		}
	}
}