#!/bin/sh
sizes="mdpi hdpi xhdpi xxhdpi xxxhdpi"
color="white"
dims="36dp"
ext="png"
source="./bower_components/material-design-icons"
target="./gearshift/src/main/res/drawable"

icons="
navigation/ic_refresh
content/ic_remove
content/ic_select_all
content/ic_forward
toggle/ic_star
toggle/ic_star_half
toggle/ic_star_outline
av/ic_play_arrow
av/ic_pause
"

for size in $sizes; do
    for icon in $icons; do
        dirname=$(dirname $icon)
        basename=$(basename $icon)

        file=$source/$dirname/drawable-$size/${basename}_${color}_${dims}.$ext

        cp $file $target-$size
    done
done
