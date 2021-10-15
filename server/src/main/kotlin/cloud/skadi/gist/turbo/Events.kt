package cloud.skadi.gist.turbo

import cloud.skadi.gist.data.Gist

sealed class GistUpdate(val gist: Gist) {
    class Removed(gist: Gist) : GistUpdate(gist)
    class Added(gist: Gist) : GistUpdate(gist)
    class Edited(gist: Gist) : GistUpdate(gist)
}