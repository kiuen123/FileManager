package com.example.filemanager.model

enum class SortMode(val displayName: String) {
    NAME_ASC("Tên (A-Z)"),
    NAME_DESC("Tên (Z-A)"),
    DATE_DESC("Ngày mới nhất"),
    DATE_ASC("Ngày cũ nhất"),
    SIZE_DESC("Kích thước lớn nhất"),
    SIZE_ASC("Kích thước nhỏ nhất"),
    TYPE("Loại file");

    val comparator: Comparator<FileItem>
        get() = when (this) {
            NAME_ASC -> compareBy({ !it.isDirectory }, { it.name.lowercase() })
            NAME_DESC -> Comparator { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    if (a.isDirectory) -1 else 1
                } else {
                    b.name.lowercase().compareTo(a.name.lowercase())
                }
            }
            DATE_DESC -> Comparator { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    if (a.isDirectory) -1 else 1
                } else {
                    b.lastModified.compareTo(a.lastModified)
                }
            }
            DATE_ASC -> compareBy({ !it.isDirectory }, { it.lastModified })
            SIZE_DESC -> Comparator { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    if (a.isDirectory) -1 else 1
                } else {
                    b.size.compareTo(a.size)
                }
            }
            SIZE_ASC -> compareBy({ !it.isDirectory }, { it.size })
            TYPE -> compareBy({ !it.isDirectory }, { it.extension }, { it.name.lowercase() })
        }
}

enum class ViewMode { LIST, GRID }

