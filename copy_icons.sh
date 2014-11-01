#!/bin/sh
sizes="mdpi hdpi xhdpi xxhdpi xxxhdpi"
color="white"
dims="36dp"
ext="png"
source="./bower_components/material-design-icons"
target="./gearshift/src/main/res/drawable"

icons="
action/ic_done
av/ic_pause
av/ic_play_arrow
content/ic_forward
content/ic_remove
content/ic_select_all
file/ic_folder_open
navigation/ic_refresh
toggle/ic_star
toggle/ic_star_half
toggle/ic_star_outline
"

for size in $sizes; do
    for icon in $icons; do
        dirname=$(dirname $icon)
        basename=$(basename $icon)

        file=$source/$dirname/drawable-$size/${basename}_${color}_${dims}.$ext

        cp $file $target-$size
    done
done
