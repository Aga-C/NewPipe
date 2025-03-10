package org.schabi.newpipe.fragments.detail;

import static android.text.TextUtils.isEmpty;
import static org.schabi.newpipe.extractor.utils.Utils.isBlank;
import static org.schabi.newpipe.util.text.TextLinkifier.SET_LINK_MOVEMENT_METHOD;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.text.HtmlCompat;

import com.google.android.material.chip.Chip;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentDescriptionBinding;
import org.schabi.newpipe.databinding.ItemMetadataBinding;
import org.schabi.newpipe.databinding.ItemMetadataTagsBinding;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.text.TextLinkifier;

import java.util.List;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public abstract class BaseDescriptionFragment extends BaseFragment {
    private final CompositeDisposable descriptionDisposables = new CompositeDisposable();
    protected FragmentDescriptionBinding binding;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentDescriptionBinding.inflate(inflater, container, false);
        setupDescription();
        setupMetadata(inflater, binding.detailMetadataLayout);
        addTagsMetadataItem(inflater, binding.detailMetadataLayout);
        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        descriptionDisposables.clear();
        super.onDestroy();
    }

    /**
     * Get the description to display.
     * @return description object
     */
    @Nullable
    protected abstract Description getDescription();

    /**
     * Get the streaming service. Used for generating description links.
     * @return streaming service
     */
    @Nullable
    protected abstract StreamingService getService();

    /**
     * Get the streaming service ID. Used for tag links.
     * @return service ID
     */
    protected abstract int getServiceId();

    /**
     * Get the URL of the described video or audio, used to generate description links.
     * @return stream URL
     */
    @Nullable
    protected abstract String getStreamUrl();

    /**
     * Get the list of tags to display below the description.
     * @return tag list
     */
    @Nullable
    public abstract List<String> getTags();

    /**
     * Add additional metadata to display.
     * @param inflater LayoutInflater
     * @param layout detailMetadataLayout
     */
    protected abstract void setupMetadata(LayoutInflater inflater, LinearLayout layout);

    private void setupDescription() {
        final Description description = getDescription();
        if (description == null || isEmpty(description.getContent())
                || description == Description.EMPTY_DESCRIPTION) {
            binding.detailDescriptionView.setVisibility(View.GONE);
            binding.detailSelectDescriptionButton.setVisibility(View.GONE);
            return;
        }

        // start with disabled state. This also loads description content (!)
        disableDescriptionSelection();

        binding.detailSelectDescriptionButton.setOnClickListener(v -> {
            if (binding.detailDescriptionNoteView.getVisibility() == View.VISIBLE) {
                disableDescriptionSelection();
            } else {
                // enable selection only when button is clicked to prevent flickering
                enableDescriptionSelection();
            }
        });
    }

    private void enableDescriptionSelection() {
        binding.detailDescriptionNoteView.setVisibility(View.VISIBLE);
        binding.detailDescriptionView.setTextIsSelectable(true);

        final String buttonLabel = getString(R.string.description_select_disable);
        binding.detailSelectDescriptionButton.setContentDescription(buttonLabel);
        TooltipCompat.setTooltipText(binding.detailSelectDescriptionButton, buttonLabel);
        binding.detailSelectDescriptionButton.setImageResource(R.drawable.ic_close);
    }

    private void disableDescriptionSelection() {
        // show description content again, otherwise some links are not clickable
        final Description description = getDescription();
        if (description != null) {
            TextLinkifier.fromDescription(binding.detailDescriptionView,
                    description, HtmlCompat.FROM_HTML_MODE_LEGACY,
                    getService(), getStreamUrl(),
                    descriptionDisposables, SET_LINK_MOVEMENT_METHOD);
        }

        binding.detailDescriptionNoteView.setVisibility(View.GONE);
        binding.detailDescriptionView.setTextIsSelectable(false);

        final String buttonLabel = getString(R.string.description_select_enable);
        binding.detailSelectDescriptionButton.setContentDescription(buttonLabel);
        TooltipCompat.setTooltipText(binding.detailSelectDescriptionButton, buttonLabel);
        binding.detailSelectDescriptionButton.setImageResource(R.drawable.ic_select_all);
    }

    protected void addMetadataItem(final LayoutInflater inflater,
                                   final LinearLayout layout,
                                   final boolean linkifyContent,
                                   @StringRes final int type,
                                   @Nullable final String content) {
        if (isBlank(content)) {
            return;
        }

        final ItemMetadataBinding itemBinding =
                ItemMetadataBinding.inflate(inflater, layout, false);

        itemBinding.metadataTypeView.setText(type);
        itemBinding.metadataTypeView.setOnLongClickListener(v -> {
            ShareUtils.copyToClipboard(requireContext(), content);
            return true;
        });

        if (linkifyContent) {
            TextLinkifier.fromPlainText(itemBinding.metadataContentView, content, null, null,
                    descriptionDisposables, SET_LINK_MOVEMENT_METHOD);
        } else {
            itemBinding.metadataContentView.setText(content);
        }

        itemBinding.metadataContentView.setClickable(true);

        layout.addView(itemBinding.getRoot());
    }

    private void addTagsMetadataItem(final LayoutInflater inflater, final LinearLayout layout) {
        final List<String> tags = getTags();

        if (tags != null && !tags.isEmpty()) {
            final var itemBinding = ItemMetadataTagsBinding.inflate(inflater, layout, false);

            tags.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(tag -> {
                final Chip chip = (Chip) inflater.inflate(R.layout.chip,
                        itemBinding.metadataTagsChips, false);
                chip.setText(tag);
                chip.setOnClickListener(this::onTagClick);
                chip.setOnLongClickListener(this::onTagLongClick);
                itemBinding.metadataTagsChips.addView(chip);
            });

            layout.addView(itemBinding.getRoot());
        }
    }

    private void onTagClick(final View chip) {
        if (getParentFragment() != null) {
            NavigationHelper.openSearchFragment(getParentFragment().getParentFragmentManager(),
                    getServiceId(), ((Chip) chip).getText().toString());
        }
    }

    private boolean onTagLongClick(final View chip) {
        ShareUtils.copyToClipboard(requireContext(), ((Chip) chip).getText().toString());
        return true;
    }
}
