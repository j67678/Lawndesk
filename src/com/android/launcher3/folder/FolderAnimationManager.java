/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications copyright 2021, Lawnchair
 */

package com.android.launcher3.folder;

import static com.android.launcher3.BubbleTextView.TEXT_ALPHA_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.folder.NineFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.graphics.IconShape.getShape;
import static com.android.launcher3.folder.FolderIcon.ICON_SCALE_PROPERTY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.Pair;
import android.util.Property;
import android.view.View;
import android.view.animation.AnimationUtils;
import ch.deletescape.lawnchair.LawnchairPreferences;
import com.android.launcher3.DeviceProfile;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ResourceUtils;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.util.Themes;

import java.util.List;

/**
 * Manages the opening and closing animations for a {@link Folder}.
 *
 * All of the animations are done in the Folder.
 * ie. When the user taps on the FolderIcon, we immediately hide the FolderIcon and show the Folder
 * in its place before starting the animation.
 */
public class FolderAnimationManager {

    private static final int FOLDER_NAME_ALPHA_DURATION = 32;

    private Folder mFolder;
    private FolderPagedView mContent;
    private GradientDrawable mFolderBackground;

    private FolderIcon mFolderIcon;
    private PreviewBackground mPreviewBackground;

    private Context mContext;
    private Launcher mLauncher;

    private final boolean mIsOpening;

    private final int mDuration;
    private final int mDelay;

    private final TimeInterpolator mFolderInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemOpenInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemCloseInterpolator;

    private final PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private final FolderGridOrganizer mPreviewVerifier;
    private final LawnchairPreferences prefs;

    public FolderAnimationManager(Folder folder, boolean isOpening) {
        mFolder = folder;
        mContent = folder.mContent;
        mFolderBackground = (GradientDrawable) mFolder.getBackground();

        mFolderIcon = folder.mFolderIcon;
        mPreviewBackground = mFolderIcon.mBackground;

        mContext = folder.getContext();
        mLauncher = folder.mLauncher;
        mPreviewVerifier = new FolderGridOrganizer(mLauncher.getDeviceProfile().inv);

        mIsOpening = isOpening;

        Resources res = mContent.getResources();
        mDuration = res.getInteger(R.integer.config_materialFolderExpandDuration);
        mDelay = res.getInteger(R.integer.config_folderDelay);

        mFolderInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.folder_interpolator);
        mLargeFolderPreviewItemOpenInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.large_folder_preview_item_open_interpolator);
        mLargeFolderPreviewItemCloseInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.large_folder_preview_item_close_interpolator);

        prefs = Utilities.getLawnchairPrefs(folder.getContext());
    }


    private static final boolean DEBUG = false;

    /**
     * Prepares the Folder for animating between open / closed states.
     */
    public AnimatorSet getAnimator() {
        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) mFolder.getLayoutParams();
        mFolderIcon.getPreviewItemManager().recomputePreviewDrawingParams();
        NineFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        final List<BubbleTextView> itemsInPreview = getPreviewIconsOnPage(0);

        DeviceProfile grid = mLauncher.getDeviceProfile();

        // Match position of the FolderIcon
        final Rect folderIconPos = new Rect();
        float scaleRelativeToDragLayer = mLauncher.getDragLayer()
                .getDescendantRectRelativeToSelf(mFolderIcon, folderIconPos);
        int scaledRadius = mPreviewBackground.getScaledRadius();
        float initialSize = mPreviewBackground.previewSize;

        // Match size/scale of icons in the preview
        float previewScale = rule.scaleForItem(itemsInPreview.size());
        float previewSize = rule.getIconSize() * previewScale;
        float initialScale = initialSize / (rule.getIconSize() * 3
                + mFolder.getPaddingLeft() + mContent.getPaddingLeft()
                + mFolder.getPaddingRight() + mContent.getPaddingRight());
        final float finalScale = 1f;
        float scale = mIsOpening ? initialScale : finalScale;
        mFolder.setPivotX(0);
        mFolder.setPivotY(0);

        // Scale the contents of the folder.
        mFolder.mContent.setScaleX(scale);
        mFolder.mContent.setScaleY(scale);
        mFolder.mContent.setPivotX(0);
        mFolder.mContent.setPivotY(0);
        mFolder.mFooter.setScaleX(scale);
        mFolder.mFooter.setScaleY(scale);
        mFolder.mFooter.setPivotX(0);
        mFolder.mFooter.setPivotY(0);

        // We want to create a small X offset for the preview items, so that they follow their
        // expected path to their final locations. ie. an icon should not move right, if it's final
        // location is to its left. This value is arbitrarily defined.
        float iconOffsetX = grid.folderCellPaddingX * initialScale;
        float iconOffsetY = grid.folderCellPaddingY * initialScale;
        float previewItemOffsetX = - ((mFolder.getPaddingLeft() + mContent.getPaddingLeft()
        final int paddingOffsetX = (int) ((mFolder.getPaddingLeft() + mContent.getPaddingLeft())
        final int paddingOffsetY = (int) ((mFolder.getPaddingTop() + mContent.getPaddingTop())

        int initialX = folderIconPos.left + mFolder.getPaddingLeft()
        int initialX = folderIconPos.left + mPreviewBackground.getOffsetX() - paddingOffsetX
                - previewItemOffsetX;
        int initialY = folderIconPos.top + mPreviewBackground.getOffsetY() - paddingOffsetY;
        final float xDistance = initialX - lp.x;
        final float yDistance = initialY - lp.y;

        // Set up the Folder background.
        final int finalColor = ColorUtils.setAlphaComponent(
                Themes.getAttrColor(mContext, R.attr.folderFillColor), 255);
        final int initialColor =
                ColorUtils.setAlphaComponent(finalColor, mPreviewBackground.getBackgroundAlpha());
        mFolderBackground.mutate();
        mFolderBackground.setColor(mIsOpening ? initialColor : finalColor);

        Rect startRect = new Rect(
                Math.round(totalOffsetX / initialScale),
                Math.round((totalOffsetX + initialSize) / initialScale),
                Math.round((paddingOffsetY + initialSize) / initialScale));
        float finalRadius = Utilities.pxFromDp(8, mContext.getResources().getDisplayMetrics());
        if (Utilities.ATLEAST_P) {
            finalRadius = Themes.getDimension(mContext, android.R.attr.dialogCornerRadius, finalRadius);
        }
        // Create the animators.
        AnimatorSet a = new AnimatorSet();

        // Initialize the Folder items' text.
//        PropertyResetListener colorResetListener =
//                new PropertyResetListener<>(TEXT_ALPHA_PROPERTY, 1f);
        for (BubbleTextView icon : mFolder.getItemsOnPage(mFolder.mContent.getCurrentPage())) {
            if (mIsOpening) {
                icon.setTextVisibility(false);
            }
            ObjectAnimator anim = icon.createTextAlphaAnimator(mIsOpening);
//            anim.addListener(colorResetListener);
            play(a, anim);
        }

        play(a, getAnimator(mFolder, View.TRANSLATION_X, xDistance, DEBUG ? xDistance : 0f));
        play(a, getAnimator(mFolder, View.TRANSLATION_Y, yDistance, DEBUG ? yDistance : 0f));
        play(a, getAnimator(mFolder.mContent, SCALE_PROPERTY, initialScale, finalScale));
        play(a, getAnimator(mFolder, SCALE_PROPERTY, initialScale, finalScale));
        play(a, getAnimator(mFolderBackground, "color", initialColor, finalColor));

        play(a, getShape().createRevealAnimator(
        play(a, IconShape.getShape().createRevealAnimator(mFolder, startRect, endRect, finalRadius, !mIsOpening));
            mPreviewBackground.fadeInBackground();
        play(a, getAnimator(mFolder.mFolderName, View.ALPHA, 0, 1),
                mIsOpening ? FOLDER_NAME_ALPHA_DURATION : 0,
                mIsOpening ? mDuration - FOLDER_NAME_ALPHA_DURATION : FOLDER_NAME_ALPHA_DURATION);

        // Translate the footer so that it tracks the bottom of the content.
        float normalHeight = mFolder.getContentAreaHeight();
        float scaledHeight = normalHeight * initialScale;
        float diff = normalHeight - scaledHeight;
        play(a, getAnimator(mFolder.mFooter, View.TRANSLATION_Y, -diff, 0f));

        // Animate the elevation midway so that the shadow is not noticeable in the background.
        int midDuration = mDuration / 2;
        Animator z = getAnimator(mFolder, View.TRANSLATION_Z, -mFolder.getElevation(), 0);
        play(a, z, mIsOpening ? midDuration : 0, midDuration);

        // Store clip variables
        CellLayout cellLayout = mContent.getCurrentCellLayout();
        boolean folderClipChildren = mFolder.getClipChildren();
        boolean folderClipToPadding = mFolder.getClipToPadding();
        boolean contentClipChildren = mContent.getClipChildren();
        boolean contentClipToPadding = mContent.getClipToPadding();
        boolean cellLayoutClipChildren = cellLayout.getClipChildren();
        boolean cellLayoutClipPadding = cellLayout.getClipToPadding();

        mFolder.setClipChildren(false);
        mFolder.setClipToPadding(false);
        mContent.setClipChildren(false);
        mContent.setClipToPadding(false);
        cellLayout.setClipChildren(false);
        cellLayout.setClipToPadding(false);

        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (!DEBUG) {
                    mFolder.setTranslationX(0.0f);
                    mFolder.setTranslationY(0.0f);
                    mFolder.setTranslationZ(0.0f);
                mFolder.mContent.setScaleX(1f);
                mFolder.mContent.setScaleY(1f);
                mFolder.mFooter.setScaleX(1f);
                mFolder.mFooter.setScaleY(1f);
                mFolder.mFooter.setTranslationX(0f);
                mFolder.mFolderName.setAlpha(1f);
                }
                mFolder.setClipChildren(folderClipChildren);
                mFolder.setClipToPadding(folderClipToPadding);
                mContent.setClipChildren(contentClipChildren);
                mContent.setClipToPadding(contentClipToPadding);
                cellLayout.setClipChildren(cellLayoutClipChildren);
                cellLayout.setClipToPadding(cellLayoutClipPadding);

            }
        });

        // We set the interpolator on all current child animators here, because the preview item
        // animators may use a different interpolator.
        for (Animator animator : a.getChildAnimations()) {
            animator.setInterpolator(mFolderInterpolator);
        }

        int radiusDiff = scaledRadius - mPreviewBackground.getRadius();
        addPreviewItemAnimators(a, initialScale / scaleRelativeToDragLayer,
                // Background can have a scaled radius in drag and drop mode, so we need to add the
                // difference to keep the preview items centered.
                previewItemOffsetX + radiusDiff, previewItemOffsetY + radiusDiff);
        return a;
    }

    /**
     * Returns the list of "preview items" on {@param page}.
     */
    private List<BubbleTextView> getPreviewIconsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mFolder.mInfo)
                .previewItemsForPage(page, mFolder.getIconsInReadingOrder());
    }

    /**
     * Animate the items on the current page.
     */
    private void addPreviewItemAnimators(AnimatorSet animatorSet, final float folderScale,
            float previewItemOffsetX, float previewItemOffsetY) {
        NineFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        boolean isOnFirstPage = mFolder.mContent.getCurrentPage() == 0;
        final List<BubbleTextView> itemsInPreview = isOnFirstPage
                : mFolderIcon.getPreviewItemsOnPage(mFolder.mContent.getCurrentPage());
        List<BubbleTextView> itemsInPreview = itemsOnPage.first;
        List<BubbleTextView> itemsNotInPreview = itemsOnPage.second;
        final int numItemsInPreview = itemsInPreview.size();
        final int numItemsInFirstPagePreview = isOnFirstPage
                ? numItemsInPreview : MAX_NUM_ITEMS_IN_PREVIEW;

        final boolean hideAppLabels = prefs.getHideAppLabels();

        if (!mIsOpening) {
            for (int i = 0; i < itemsNotInPreview.size(); ++i) {
                final BubbleTextView btv = itemsNotInPreview.get(i);

                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        btv.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        btv.setVisibility(View.VISIBLE);
                        if (!hideAppLabels) {
                            btv.setTextVisibility(true);
                        }
                    }
                });
            }
        }

        TimeInterpolator previewItemInterpolator = getPreviewItemInterpolator();

        ShortcutAndWidgetContainer cwc = mContent.getPageAt(0).getShortcutsAndWidgets();
        for (int i = 0; i < numItemsInPreview; ++i) {
            final BubbleTextView btv = itemsInPreview.get(i);
            CellLayout.LayoutParams btvLp = (CellLayout.LayoutParams) btv.getLayoutParams();

            // Calculate the final values in the LayoutParams.
            btvLp.isLockedToGrid = true;
            cwc.setupLp(btv);

            // Match scale of icons in the preview of the items on the first page.
            float previewScale = rule.scaleForItem(numItemsInFirstPagePreview);
            float previewSize = rule.getIconSize() * previewScale;
            float iconScale = previewSize / itemsInPreview.get(i).getIconSize();

            final float initialScale = iconScale / folderScale;
            final float finalScale = 1f;
            float scale = mIsOpening ? initialScale : finalScale;
            btv.setScaleX(scale);
            btv.setScaleY(scale);

            // Match positions of the icons in the folder with their positions in the preview
            rule.computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, mTmpParams);
            // The PreviewLayoutRule assumes that the icon size takes up the entire width so we
            // offset by the actual size.
            float iconOffsetX = ((btvLp.width - btv.getIconSize()) * iconScale);

            final float previewPosX = ((mTmpParams.transX + previewItemOffsetX) / folderScale);
            final float previewPosY = ((mTmpParams.transY + previewItemOffsetY) / folderScale);

            final float xDistance = previewPosX - btvLp.x;
            final float yDistance = previewPosY - btvLp.y;

            Animator translationX = getAnimator(btv, View.TRANSLATION_X, xDistance, DEBUG ? xDistance : 0f);
            translationX.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationX);

            Animator translationY = getAnimator(btv, View.TRANSLATION_Y, yDistance, DEBUG ? yDistance : 0f);
            translationY.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationY);

            Animator scaleAnimator = getAnimator(btv, SCALE_PROPERTY, initialScale, DEBUG ? initialScale : finalScale);
            scaleAnimator.setInterpolator(previewItemInterpolator);
            play(animatorSet, scaleAnimator);
            if (mFolder.getItemCount() > MAX_NUM_ITEMS_IN_PREVIEW) {
                // These delays allows the preview items to move as part of the Folder's motion,
                // and its only necessary for large folders because of differing interpolators.
                int delay = mIsOpening ? mDelay : mDelay * 2;
                if (mIsOpening) {
                    translationX.setStartDelay(delay);
                    translationY.setStartDelay(delay);
                    scaleAnimator.setStartDelay(delay);
                }
                translationX.setDuration(translationX.getDuration() - delay);
                translationY.setDuration(translationY.getDuration() - delay);
                scaleAnimator.setDuration(scaleAnimator.getDuration() - delay);
            }

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    // Necessary to initialize values here because of the start delay.
                    if (mIsOpening) {
                        btv.setTranslationX(xDistance);
                        btv.setTranslationY(yDistance);
                        btv.setScaleX(initialScale);
                        btv.setScaleY(initialScale);
                    }
                    if (hideAppLabels) {
                        btv.setTextVisibility(false);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (!DEBUG) {
                        btv.setTranslationX(0.0f);
                        btv.setTranslationY(0.0f);
                        btv.setScaleX(1f);
                        btv.setScaleY(1f);
                    mFolderIcon.setIconScale(1f);
                    if (!hideAppLabels) {
                        btv.setTextVisibility(true);
                    }
                }
            });
        }
    }

    private void play(AnimatorSet as, Animator a) {
        play(as, a, a.getStartDelay(), mDuration);
    }

    private void play(AnimatorSet as, Animator a, long startDelay, int duration) {
        a.setStartDelay(startDelay);
        a.setDuration(duration);
        as.play(a);
    }

    private TimeInterpolator getPreviewItemInterpolator() {
        if (mFolder.getItemCount() > MAX_NUM_ITEMS_IN_PREVIEW) {
            // With larger folders, we want the preview items to reach their final positions faster
            // (when opening) and later (when closing) so that they appear aligned with the rest of
            // the folder items when they are both visible.
            return mIsOpening
                    ? mLargeFolderPreviewItemOpenInterpolator
                    : mLargeFolderPreviewItemCloseInterpolator;
        }
        return mFolderInterpolator;
    }

    private Animator getAnimator(View view, Property property, float v1, float v2) {
        return mIsOpening
                ? ObjectAnimator.ofFloat(view, property, v1, v2)
                : ObjectAnimator.ofFloat(view, property, v2, v1);
    }

    private Animator getAnimator(GradientDrawable drawable, String property, int v1, int v2) {
        return mIsOpening
                ? ObjectAnimator.ofArgb(drawable, property, v1, v2)
                : ObjectAnimator.ofArgb(drawable, property, v2, v1);
    }
}
