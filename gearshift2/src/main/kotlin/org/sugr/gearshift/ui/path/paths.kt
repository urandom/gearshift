package org.sugr.gearshift.ui.path

import org.sugr.gearshift.R

class TorrentListPath: Path {
    override val layout = R.layout.torrent_list_content
    override val title = R.string.app_name
}

class FirstTimeProfileEditorPath: Path {
    override val layout = R.layout.first_time_profile_editor
    override val depth = 1
    override val title = R.string.new_profile
}
