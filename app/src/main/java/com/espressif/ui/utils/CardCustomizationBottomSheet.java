// =============================================================
// المسار: app/src/main/java/com/espressif/ui/utils/CardCustomizationBottomSheet.java
// =============================================================
// BottomSheet لتخصيص البطاقة: لون الخلفية، شكل البطاقة، الأيقونة
// =============================================================

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.rainmaker.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;

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

        // ===== 1. عجلة الألوان =====
        ColorPicker colorPicker = view.findViewById(R.id.color_picker);
        SVBar svBar = view.findViewById(R.id.sv_bar);
        colorPicker.addSVBar(svBar);

        int currentColor = DeviceIconManager.getSavedColor(ctx, deviceId);
        if (currentColor != Color.TRANSPARENT) {
            colorPicker.setColor(currentColor);
        } else {
            colorPicker.setColor(Color.WHITE);
        }

        colorPicker.setOnColorChangedListener(color -> {
            DeviceIconManager.saveColor(ctx, deviceId, color);
            if (onChangedListener != null) onChangedListener.run();
        });

        // ===== 2. اختيار شكل البطاقة =====
        RecyclerView styleRecycler = view.findViewById(R.id.rv_card_styles);
        styleRecycler.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
        CardStyleAdapter styleAdapter = new CardStyleAdapter(ctx, deviceId, styleId -> {
            DeviceIconManager.saveCardStyle(ctx, deviceId, styleId);
            if (onChangedListener != null) onChangedListener.run();
        });
        styleRecycler.setAdapter(styleAdapter);

        // ===== 3. اختيار الأيقونة =====
        RecyclerView iconRecycler = view.findViewById(R.id.rv_icons);
        iconRecycler.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
        IconPickerAdapter iconAdapter = new IconPickerAdapter(ctx, deviceId, iconKey -> {
            DeviceIconManager.saveIconKey(ctx, deviceId, iconKey);
            if (onChangedListener != null) onChangedListener.run();
        });
        iconRecycler.setAdapter(iconAdapter);

        // ===== 4. زر استعادة الافتراضي =====
        view.findViewById(R.id.btn_reset_default).setOnClickListener(v -> {
            DeviceIconManager.resetDeviceCustomizations(ctx, deviceId);
            if (onChangedListener != null) onChangedListener.run();
            dismiss();
        });
    }

    // ===== محول أنماط البطاقة =====
    static class CardStyleAdapter extends RecyclerView.Adapter<CardStyleAdapter.ViewHolder> {
        private final Context context;
        private final String deviceId;
        private final OnStyleSelectedListener listener;
        private final DeviceIconManager.CardStyle[] styles = DeviceIconManager.CardStyle.values();

        interface OnStyleSelectedListener {
            void onStyleSelected(int styleId);
        }

        CardStyleAdapter(Context context, String deviceId, OnStyleSelectedListener listener) {
            this.context = context;
            this.deviceId = deviceId;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_card_style, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DeviceIconManager.CardStyle style = styles[position];
            holder.tvLabel.setText(style.label);

            // تطبيق الشكل على البطاقة المعاينة
            float density = context.getResources().getDisplayMetrics().density;
            int currentStyleId = DeviceIconManager.getSavedCardStyle(context, deviceId);
            boolean isSelected = currentStyleId == style.id;

            if (style == DeviceIconManager.CardStyle.CIRCLE) {
                // الدائري يحتاج إلى ارتفاع وعرض متساويين، لكننا نضبطه في المعاينة فقط
                holder.preview.setRadius(holder.preview.getHeight() / 2f);
            } else {
                holder.preview.setRadius(style.radiusDp * density);
            }

            holder.preview.setCardElevation(isSelected ? 8f : 2f);
            holder.preview.setStrokeColor(isSelected ?
                    context.getColor(R.color.colorPrimary) : Color.TRANSPARENT);
            holder.preview.setStrokeWidth(isSelected ? 4 : 0);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onStyleSelected(style.id);
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return styles.length;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLabel;
            MaterialCardView preview;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvLabel = itemView.findViewById(R.id.tv_style_label);
                preview = itemView.findViewById(R.id.card_preview);
            }
        }
    }

    // ===== محول الأيقونات =====
    static class IconPickerAdapter extends RecyclerView.Adapter<IconPickerAdapter.ViewHolder> {
        private final Context context;
        private final String deviceId;
        private final OnIconSelectedListener listener;
        private final List<DeviceIconManager.IconOption> icons = DeviceIconManager.getIconOptions();

        interface OnIconSelectedListener {
            void onIconSelected(String iconKey);
        }

        IconPickerAdapter(Context context, String deviceId, OnIconSelectedListener listener) {
            this.context = context;
            this.deviceId = deviceId;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_icon_picker_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DeviceIconManager.IconOption icon = icons.get(position);
            holder.ivIcon.setImageResource(icon.resId);
            holder.tvLabel.setText(icon.label);

            String saved = DeviceIconManager.getSavedIconKey(context, deviceId);
            boolean isSelected = icon.key.equals(saved);
            holder.itemView.setSelected(isSelected);
            holder.itemView.setBackgroundColor(isSelected ?
                    context.getColor(R.color.colorPrimary) & 0x33FFFFFF : Color.TRANSPARENT);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onIconSelected(icon.key);
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return icons.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvLabel;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_icon_preview);
                tvLabel = itemView.findViewById(R.id.tv_icon_label);
            }
        }
    }
}
