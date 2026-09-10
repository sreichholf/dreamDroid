package net.reichholf.dreamdroid.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;

/**
 * Shows a {@link PopupMenu} at a point inside a {@link ViewGroup}.
 * Compose list rows have no View anchors; anchoring to the fragment root pins the
 * menu at the top-left. A 1×1 temporary child at the tap location restores row positioning.
 */
public final class AnchorPopup {
	private AnchorPopup() {
	}

	public interface Configurer {
		void configure(@NonNull PopupMenu menu);
	}

	public static void showAt(
			@NonNull ViewGroup root,
			int xInRoot,
			int yInRoot,
			@NonNull Configurer configurer
	) {
		View anchor = new View(root.getContext());
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
		lp.leftMargin = Math.max(0, xInRoot);
		lp.topMargin = Math.max(0, yInRoot);
		root.addView(anchor, lp);
		anchor.post(() -> {
			if (anchor.getParent() == null) {
				return;
			}
			PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
			menu.setOnDismissListener(m -> {
				if (anchor.getParent() instanceof ViewGroup) {
					((ViewGroup) anchor.getParent()).removeView(anchor);
				}
			});
			configurer.configure(menu);
			menu.show();
		});
	}

	/** Convert window coordinates to offsets inside {@code root}. */
	public static void showAtWindow(
			@NonNull ViewGroup root,
			int windowX,
			int windowY,
			@NonNull Configurer configurer
	) {
		int[] loc = new int[2];
		root.getLocationOnScreen(loc);
		showAt(root, windowX - loc[0], windowY - loc[1], configurer);
	}
}
