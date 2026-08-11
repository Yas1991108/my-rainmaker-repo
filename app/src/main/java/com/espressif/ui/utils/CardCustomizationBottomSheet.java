package com.espressif.ui.utils;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.rainmaker.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;

import java.util.ArrayList;
import java.util.List;

public class CardCustomizationBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DEVICE_ID = "device_id";
    private String deviceId;
    private Runnable onChangedListener;

    public static CardCustomizationBottomSheet newInstance(String deviceId) {
        CardCustomizationBottomSheet fragment = new CardCustomizationBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnCustomizationChanged(Runnable runnable) {
        this.onChangedListener = runnable;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_card_customization, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            deviceId = getArguments().getString(ARG_DEVICE_ID);
        }

        Context ctx = getContext();
        if (ctx == null || deviceId == null) return;

        RecyclerView recyclerView = view.findViewById(R.id.rv_options);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));

        // ===== استخدام أيقونات موجودة في المشروع =====
        List<OptionItem> options = new ArrayList<>();
        options.add(new OptionItem(R.drawable.ic_info, ctx.getString(R.string.choose_icon), "icon"));
        options.add(new OptionItem(R.drawable.ic_brightness_high, ctx.getString(R.string.choose_background_color), "color"));
        options.add(new OptionItem(R.drawable.ic_device, ctx.getString(R.string.choose_card_style), "shape"));
        options.add(new OptionItem(R.drawable.ic_refresh, ctx.getString(R.string.reset_to_default), "reset"));

        OptionAdapter adapter = new OptionAdapter(options, option -> {
            switch (option.id) {
                case "icon":
                    showIconPicker(ctx);
                    break;
                case "color":
                    showColorPicker(ctx);
                    break;
                case "shape":
                    showShapePicker(ctx);
                    break;
                case "reset":
                    DeviceIconManager.resetDeviceCustomizations(ctx, deviceId);
                    if (onChangedListener != null) onChangedListener.run();
                    dismiss();
                    break;
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void showIconPicker(Context ctx) {
        List<DeviceIconManager.IconOption> icons = DeviceIconManager.getIconOptions();
        String[] labels = new String[icons.size()];
        for (int i = 0; i < icons.size(); i++) {
            labels[i] = icons.get(i).label;
        }

        int currentIndex = 0;
        String savedKey = DeviceIconManager.getSavedIconKey(ctx, deviceId);
        for (int i = 0; i < icons.size(); i++) {
            if (icons.get(i).key.equals(savedKey)) {
                currentIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.choose_icon)
                .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> {
                    DeviceIconManager.saveIconKey(ctx, deviceId, icons.get(which).key);
                    if (onChangedListener != null) onChangedListener.run();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showColorPicker(Context ctx) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_color_picker, null);
        builder.setView(view);

        ColorPicker colorPicker = view.findViewById(R.id.color_picker);
        SVBar svBar = view.findViewById(R.id.sv_bar);
        colorPicker.addSVBar(svBar);

        int currentColor = DeviceIconManager.getSavedColor(ctx, deviceId);
        if (currentColor != Color.TRANSPARENT) {
            colorPicker.setColor(currentColor);
        } else {
            colorPicker.setColor(Color.WHITE);
        }

        builder.setTitle(R.string.choose_background_color);
        builder.setPositiveButton(R.string.btn_save, (dialog, which) -> {
            DeviceIconManager.saveColor(ctx, deviceId, colorPicker.getColor());
            if (onChangedListener != null) onChangedListener.run();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    private void showShapePicker(Context ctx) {
        DeviceIconManager.CardStyle[] styles = DeviceIconManager.CardStyle.values();
        String[] labels = new String[styles.length];
        for (int i = 0; i < styles.length; i++) {
            labels[i] = styles[i].label;
        }

        int currentStyle = DeviceIconManager.getSavedCardStyle(ctx, deviceId);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.choose_card_style)
                .setSingleChoiceItems(labels, currentStyle, (dialog, which) -> {
                    DeviceIconManager.saveCardStyle(ctx, deviceId, which);
                    if (onChangedListener != null) onChangedListener.run();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    static class OptionItem {
        int iconRes;
        String title;
        String id;

        OptionItem(int iconRes, String title, String id) {
            this.iconRes = iconRes;
            this.title = title;
            this.id = id;
        }
    }

    static class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.ViewHolder> {
        private final List<OptionItem> options;
        private final OnOptionClickListener listener;

        interface OnOptionClickListener {
            void onOptionClick(OptionItem option);
        }

        OptionAdapter(List<OptionItem> options, OnOptionClickListener listener) {
            this.options = options;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_customization_option, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OptionItem item = options.get(position);
            holder.ivIcon.setImageResource(item.iconRes);
            holder.tvTitle.setText(item.title);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onOptionClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return options.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_option_icon);
                tvTitle = itemView.findViewById(R.id.tv_option_title);
            }
        }
    }
}
