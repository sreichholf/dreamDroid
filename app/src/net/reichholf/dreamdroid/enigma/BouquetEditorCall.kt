package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * One bouquet editor command: its [page] and [params]. The Dreambox's WebBouquetEditor serves it
 * under `/bouqueteditor/web/`, OpenWebif under `/bouqueteditor/web/` and `/bouqueteditor/api/`.
 * Both read the same parameter names (OpenWebif's `BQE.py` at e46534f, `buildCommand`); only
 * the answer differs. `mode` is 0 for TV, 1 for radio.
 */
internal class BouquetEditorCall private constructor(
    val page: String,
    val params: List<NameValuePair>
) {
    companion object {
        fun addBouquet(mode: BouquetMode, name: String) =
            BouquetEditorCall("addbouquet", listOf(NameValuePair("name", name), mode.param()))

        fun removeBouquet(mode: BouquetMode, bouquetRef: String) = BouquetEditorCall(
            "removebouquet",
            listOf(NameValuePair("sBouquetRef", bouquetRef), mode.param())
        )

        fun moveBouquet(mode: BouquetMode, bouquetRef: String, position: Int) = BouquetEditorCall(
            "movebouquet",
            listOf(
                NameValuePair("sBouquetRef", bouquetRef),
                mode.param(),
                NameValuePair("position", position.toString())
            )
        )

        /** `renameservice` with the bouquet as `sRef` and no `sBouquetRef`. */
        fun renameBouquet(mode: BouquetMode, bouquetRef: String, newName: String) =
            BouquetEditorCall(
                "renameservice",
                listOf(
                    NameValuePair("sRef", bouquetRef),
                    mode.param(),
                    NameValuePair("newName", newName)
                )
            )

        /** An empty `sRefBefore` appends. */
        fun addService(bouquetRef: String, serviceRef: String) = BouquetEditorCall(
            "addservicetobouquet",
            listOf(
                NameValuePair("sBouquetRef", bouquetRef),
                NameValuePair("sRef", serviceRef),
                NameValuePair("sRefBefore", "")
            )
        )

        fun removeService(bouquetRef: String, serviceRef: String) = BouquetEditorCall(
            "removeservice",
            listOf(NameValuePair("sBouquetRef", bouquetRef), NameValuePair("sRef", serviceRef))
        )

        fun moveService(mode: BouquetMode, bouquetRef: String, serviceRef: String, position: Int) =
            BouquetEditorCall(
                "moveservice",
                listOf(
                    NameValuePair("sBouquetRef", bouquetRef),
                    NameValuePair("sRef", serviceRef),
                    NameValuePair("position", position.toString()),
                    mode.param()
                )
            )

        fun renameService(
            bouquetRef: String,
            serviceRef: String,
            beforeRef: String,
            newName: String
        ) = BouquetEditorCall(
            "renameservice",
            listOf(
                NameValuePair("sBouquetRef", bouquetRef),
                NameValuePair("sRef", serviceRef),
                NameValuePair("sRefBefore", beforeRef),
                NameValuePair("newName", newName)
            )
        )

        fun addMarker(bouquetRef: String, name: String, beforeRef: String) = BouquetEditorCall(
            "addmarkertobouquet",
            listOf(
                NameValuePair("sBouquetRef", bouquetRef),
                NameValuePair("Name", name),
                NameValuePair("sRefBefore", beforeRef)
            )
        )

        /** The box writes `<fileName>.tar` into its `/tmp` and answers with that name. */
        fun backup(fileName: String) =
            BouquetEditorCall("backup", listOf(NameValuePair("Filename", fileName)))
    }
}

internal fun BouquetMode.param() = NameValuePair(
    "mode",
    when (this) {
        BouquetMode.Tv -> "0"
        BouquetMode.Radio -> "1"
    }
)
