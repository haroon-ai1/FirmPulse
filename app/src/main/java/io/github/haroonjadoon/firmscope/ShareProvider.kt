package io.github.haroonjadoon.firmscope

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Read-only, temporary URI grants for explicitly shared result files. */
class ShareProvider : ContentProvider() {
    override fun onCreate()=true
    private fun file(uri: Uri): File {
        require(uri.pathSegments.size==1) { "Invalid share address" }
        val name=uri.lastPathSegment ?: error("Missing file")
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid filename" }
        val base=File(context!!.cacheDir,"shares").canonicalFile
        val selected=File(base,name).canonicalFile
        require(selected.parentFile==base && selected.isFile) { "File unavailable" }
        return selected
    }
    override fun getType(uri: Uri)=when(file(uri).extension) { "png"->"image/png";"csv"->"text/csv";"json"->"application/json";else->"text/plain" }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode=="r") { "Read-only" };return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val selected=file(uri)
        val fields=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(fields).apply { addRow(fields.map { when(it) { OpenableColumns.DISPLAY_NAME->selected.name;OpenableColumns.SIZE->selected.length();else->null } }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri?=throw UnsupportedOperationException()
    override fun update(uri: Uri,values: ContentValues?,selection: String?,args: Array<out String>?): Int=throw UnsupportedOperationException()
    override fun delete(uri: Uri,selection: String?,args: Array<out String>?): Int=throw UnsupportedOperationException()
}
