package dev.captience.realisticdamage.client.mixin;

import java.util.List;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.captience.realisticdamage.client.BloodiedItems;
import dev.captience.realisticdamage.client.BloodiedState;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The third pass over a batch of items (2.0). Vanilla draws every item, then
 * goes round again for the enchantment glint; this goes round once more for
 * blood, the same way and for the same reason: an overlay on the item's own
 * quads has to come after the quads, in the same batch, or there is nothing
 * for it to land on.
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> {
	@Shadow
	@Final
	private QuadInstance quadInstance;

	@Inject(method = "buildGroup", at = @At("TAIL"))
	private void realisticdamage$blood(FeatureFrameContext context, List<ItemFeatureRenderer.Submit> submits,
			CallbackInfo ci) {
		for (ItemFeatureRenderer.Submit submit : submits) {
			BloodiedItems.Look look = ((BloodiedState) (Object) submit).realisticdamage$blood();
			// The glowing outline is its own draw of the same item; blood is
			// not part of a silhouette.
			if (look == null || submit.outlineColor() != 0) {
				continue;
			}
			quadInstance.setLightCoords(submit.lightCoords());
			quadInstance.setOverlayCoords(submit.overlayCoords());
			quadInstance.setColor(BloodiedItems.tint());
			int quads = 0;
			for (BakedQuad quad : submit.quads()) {
				TextureAtlasSprite sprite = quad.materialInfo().sprite();
				RenderType type = BloodiedItems.typeFor(sprite, look);
				if (type == null) {
					continue;
				}
				getVertexBuilder(type).putBakedQuad(submit.pose(), BloodiedItems.remap(quad, sprite), quadInstance);
				quads++;
			}
			BloodiedItems.countDrawn(quads);
		}
	}
}
