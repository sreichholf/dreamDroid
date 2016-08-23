package net.reichholf.dreamdroid.fragment.dialogs;

/**
 * Created by reichi on 22/08/16.
 */
public class BottomSheetActionDialog extends AbstractBottomSheetDialog {
	protected void finishDialog(int action, Object details) {
		ActionDialog.DialogActionListener listener = (ActionDialog.DialogActionListener) getActivity();
		if(listener != null)
			listener.onDialogAction(action, details, getTag());
		dismiss();
	}
}
