package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.util.InjuryMessages;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * The off switch for the white line at the bottom of the screen (1.6).
 *
 * <p>The client decides, not the server. That is the rule DESIGN.md set in 1.1
 * for injury audio — "style is presentation, so the server never decides it" —
 * and the text describes the very same transitions the audio does. It is also
 * the only answer that is right on a server, where one admin's taste must not
 * silence, or force, the words for everybody; and it keeps a vanilla client on
 * a modded server seeing the injury text, because what the server sends is
 * still ordinary system chat.
 *
 * <p>Nothing new is mixed in. {@code fabric-message-api-v1} already injects at
 * the head of {@code ChatListener.handleOverlay} and exposes the veto as
 * {@link ClientReceiveMessageEvents#ALLOW_GAME}; refusing there skips both the
 * overlay and the narrator call that follows it.
 *
 * <p>That narrator call is why {@link ClientEffectsConfig#disableAllEffects()}
 * leaves this alone: silencing the line silences a screen-reader player's only
 * injury cue, and the panic button must not make that choice on their behalf.
 */
public final class InjuryMessageFilter {
	private InjuryMessageFilter() {}

	private static @Nullable String lastSuppressedKey;

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			// Guarded on overlay first: this event also carries ordinary system
			// chat — deaths, /say, command feedback — and the action bar is the
			// only channel this switch is about.
			if (!overlay || ClientEffectsConfig.get().showInjuryMessages) {
				return true;
			}
			// Identified by the key the server chose, never by the English it
			// renders to. ClientboundSystemChatPacket carries the translatable
			// unresolved, so the key survives the wire; and every Notifier line
			// is a bare Component.translatable with no siblings and no style, so
			// the root contents IS the key. That exactness is what stops this
			// from eating vanilla's "Now Playing" or another mod's action bar.
			if (message.getContents() instanceof TranslatableContents contents
					&& InjuryMessages.isBodyReport(contents.getKey())) {
				lastSuppressedKey = contents.getKey();
				return false;
			}
			return true;
		});
	}

	/**
	 * The last line this filter swallowed, mirroring
	 * {@code Notifier.lastMessageKey()} on the other side of the wire. A test
	 * hook: a screenshot cannot tell a line that was never sent from one that
	 * was sent and refused, and those are the two states worth distinguishing.
	 * Client thread only.
	 */
	public static @Nullable String lastSuppressedKey() {
		return lastSuppressedKey;
	}

	public static void clearLastSuppressedKey() {
		lastSuppressedKey = null;
	}
}
