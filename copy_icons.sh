#!/bin/sh
ext="png"
source="./bower_components/material-design-icons"
target="./gearshift/src/main/res/drawable"

copy_icons() {
    for size in $1; do
        for icon in $2; do
            dirname=$(dirname $icon)
            basename=$(basename $icon)

            file=$source/$dirname/drawable-$size/${basename}_${3}_${4}.$ext

            cp $file $target-$size
        done
    done
}

sizes="mdpi hdpi xhdpi xxhdpi xxxhdpi"
color="white"
dims="36dp"

icons="
action/ic_done
av/ic_pause
av/ic_play_arrow
content/ic_forward
content/ic_remove
content/ic_select_all
navigation/ic_refresh
toggle/ic_star
toggle/ic_star_half
toggle/ic_star_outline
"

copy_icons "$sizes" "$icons" $color $dims

icons="
file/ic_folder_open
"

copy_icons "$sizes" "$icons" 'grey600' $dims
