import type { CatalogNode } from '~/api/topic'

/**
 * 递归收集树中所有非 null 的 articleId
 */
export function collectArticleIds(tree: CatalogNode[]): number[] {
  const ids: number[] = []
  for (const node of tree) {
    if (node.articleId != null) {
      ids.push(node.articleId)
    }
    if (node.children?.length) {
      ids.push(...collectArticleIds(node.children))
    }
  }
  return ids
}

/**
 * 将树展平为数组，递归处理子节点，保留层级和父子关系字段
 */
export function flattenTree(tree: CatalogNode[]): CatalogNode[] {
  const result: CatalogNode[] = []
  for (const node of tree) {
    result.push(node)
    if (node.children?.length) {
      result.push(...flattenTree(node.children))
    }
  }
  return result
}

/**
 * 将平铺节点数组重建为树形结构，按 sort 升序排列子节点
 */
export function buildTree(nodes: CatalogNode[]): CatalogNode[] {
  const map = new Map<number, CatalogNode>()
  const roots: CatalogNode[] = []

  // 初始化每个节点的 children
  for (const node of nodes) {
    node.children = []
    if (node.id != null) {
      map.set(node.id, node)
    }
  }

  for (const node of nodes) {
    if (node.parentId === 0 || node.parentId == null) {
      roots.push(node)
    } else {
      const parent = map.get(node.parentId)
      if (parent) {
        parent.children.push(node)
      } else {
        roots.push(node)
      }
    }
  }

  // 按 sort 升序排列子节点
  const sortChildren = (list: CatalogNode[]) => {
    list.sort((a, b) => a.sort - b.sort)
    for (const node of list) {
      if (node.children.length) {
        sortChildren(node.children)
      }
    }
  }
  sortChildren(roots)

  return roots
}

/**
 * 按数组顺序重新赋值 sort（0, 1, 2, ...）
 */
export function recalculateSort(siblings: CatalogNode[]): void {
  for (let i = 0; i < siblings.length; i++) {
    siblings[i].sort = i
  }
}
