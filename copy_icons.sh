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

# Toolbar
icons="
action/ic_done
action/ic_search
av/ic_pause
av/ic_play_arrow
av/ic_my_library_add
content/ic_add
content/ic_forward
content/ic_remove
content/ic_select_all
navigation/ic_arrow_back
navigation/ic_close
navigation/ic_refresh
toggle/ic_star
toggle/ic_star_half
toggle/ic_star_outline
"

copy_icons "$sizes" "$icons" "white" "24dp"

icons="
file/ic_folder_open
navigation/ic_expand_more
"

copy_icons "$sizes" "$icons" 'grey600' "36dp"

icons="
action/ic_info
action/ic_settings
action/ic_settings_remote
"

copy_icons "$sizes" "$icons" 'black' "18dp"
