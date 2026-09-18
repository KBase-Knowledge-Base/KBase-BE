package com.kbase.document.service;

import java.util.List;

import com.kbase.category.entity.Category;
import com.kbase.folder.entity.Folder;
import com.kbase.tag.entity.Tag;

record ResolvedDocumentMetadata(Folder folder, Category category, List<Tag> tags) {
}
