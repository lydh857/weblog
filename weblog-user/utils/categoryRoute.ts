import type { CategoryTreeVO } from '~/api/category'

export interface CategoryRouteMatch {
  category: CategoryTreeVO | null
  subCategory: CategoryTreeVO | null
}

export function findCategoryBySlug(categories: CategoryTreeVO[], slug: string): CategoryRouteMatch {
  for (const category of categories) {
    if (category.slug === slug) {
      return { category, subCategory: null }
    }
    for (const child of category.children || []) {
      if (child.slug === slug) {
        return { category, subCategory: child }
      }
    }
  }
  return { category: null, subCategory: null }
}

export function buildCategoryPathById(
  categories: CategoryTreeVO[],
  categoryId: number | null | undefined,
  subCategoryId: number | null | undefined,
): string {
  const normalizedCategoryId = categoryId == null ? null : Number(categoryId)
  const normalizedSubCategoryId = subCategoryId == null ? null : Number(subCategoryId)

  if (normalizedSubCategoryId) {
    for (const category of categories) {
      const sub = (category.children || []).find(item => Number(item.id) === normalizedSubCategoryId)
      if (sub?.slug) {
        return `/category/${sub.slug}`
      }
    }
  }

  if (normalizedCategoryId) {
    const category = categories.find(item => Number(item.id) === normalizedCategoryId)
    if (category?.slug) {
      return `/category/${category.slug}`
    }
  }

  return '/category'
}
