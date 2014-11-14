package org.sugr.gearshift.ui.util;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class ExpandAnimation {
    private View target;

    public ExpandAnimation(View target) {
        this.target = target;
    }

    public void expand() {
        start(true);
    }

    public void collapse() {
        start(false);
    }

    private void start(final boolean expand) {
        if (expand) {
            target.setAlpha(0);
            target.setVisibility(View.VISIBLE);
        } else {
            target.setAlpha(1);
            target.setVisibility(View.GONE);
        }

        final ViewTreeObserver observer = target.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);

                final int height = target.getHeight();
                if (expand) {
                    target.getLayoutParams().height = 0;
                    target.requestLayout();
                } else {
                    target.setVisibility(View.VISIBLE);
                }

                final Animation a = new Animation() {
                    @Override protected void applyTransformation(float interpolatedTime, Transformation t) {
                        if (interpolatedTime == 1) {
                            if (expand) {
                                target.animate().alpha(1);
                            } else {
                                target.setVisibility(View.GONE);
                            }
                            target.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                        } else {
                            if (expand) {
                                target.getLayoutParams().height = (int) (height * interpolatedTime);
                            } else {
                                target.getLayoutParams().height = height - (int)(height * interpolatedTime);
                            }
                        }

                        target.requestLayout();
                    }

                    @Override
                    public boolean willChangeBounds() {
                        return true;
                    }
                };

                Resources resources = target.getContext().getResources();
                int shortDuration = resources.getInteger(android.R.integer.config_shortAnimTime);
                int sizeDuration = (int) (height / resources.getDisplayMetrics().density);
                int duration = (int) Math.min(shortDuration, Math.max(sizeDuration, shortDuration * 1.5));

                a.setDuration(duration);

                if (expand) {
                    target.startAnimation(a);
                } else {
                    target.animate().alpha(0).withEndAction(new Runnable() {
                        @Override public void run() {
                            target.startAnimation(a);
                        }
                    });
                }

                return false;
            }
        });
    }
}
