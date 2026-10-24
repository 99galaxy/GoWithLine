package com.gowithline.mock.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.gowithline.mock.R;
import com.gowithline.mock.map.MapLayer;
import com.gowithline.mock.map.TileSources;

/**
 * Map layer picker shared by the settings screen. It only reports the chosen
 * layer; the main activity applies it to the live map and converts the route.
 */
public final class LayerPickerDialog {
    public interface Callback {
        void onLayerChosen(String layerId, String customUrl,
                           MapLayer.CoordSystem customCoord);
    }

    private LayerPickerDialog() {
    }

    public static void show(Activity activity, String currentId,
                            String customUrl, MapLayer.CoordSystem customCoord,
                            Callback callback) {
        String[] items = new String[]{
                activity.getString(R.string.layer_osm),
                activity.getString(R.string.layer_custom)
        };
        int checked = MapLayer.ID_CUSTOM.equals(currentId) ? 1 : 0;

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_layer_title)
                .setMessage(R.string.dialog_layer_note)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 1) {
                        showCustomDialog(activity, customUrl, customCoord, callback);
                    } else {
                        callback.onLayerChosen(MapLayer.ID_OSM, null,
                                MapLayer.CoordSystem.WGS84);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private static void showCustomDialog(Activity activity, String currentUrl,
                                         MapLayer.CoordSystem currentCoord,
                                         Callback callback) {
        View content = activity.getLayoutInflater()
                .inflate(R.layout.dialog_custom_tile, null);
        TextInputLayout urlLayout = content.findViewById(R.id.customUrlLayout);
        TextInputEditText urlEdit = content.findViewById(R.id.customUrl);
        RadioGroup coordGroup = content.findViewById(R.id.customCoordGroup);

        if (currentUrl != null) {
            urlEdit.setText(currentUrl);
        }
        if (currentCoord == MapLayer.CoordSystem.GCJ02) {
            coordGroup.check(R.id.customCoordGcj);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_custom_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_confirm, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String url = urlEdit.getText() == null
                                    ? "" : urlEdit.getText().toString().trim();
                            if (!TileSources.isValidTemplate(url)) {
                                urlLayout.setError(activity.getString(
                                        R.string.custom_url_invalid));
                                return;
                            }
                            urlLayout.setError(null);
                            MapLayer.CoordSystem coordSystem =
                                    coordGroup.getCheckedRadioButtonId() == R.id.customCoordGcj
                                            ? MapLayer.CoordSystem.GCJ02
                                            : MapLayer.CoordSystem.WGS84;
                            try {
                                TileSources.custom(url, coordSystem);
                            } catch (IllegalArgumentException e) {
                                urlLayout.setError(activity.getString(
                                        R.string.custom_url_invalid));
                                return;
                            }
                            dialog.dismiss();
                            callback.onLayerChosen(MapLayer.ID_CUSTOM, url, coordSystem);
                        }));
        dialog.show();
    }
}
