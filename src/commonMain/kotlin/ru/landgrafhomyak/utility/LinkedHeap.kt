@file:Suppress("LiftReturnOrAssignment")
@file:OptIn(ExperimentalContracts::class)

package ru.landgrafhomyak.utility

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("FunctionName")
abstract class LinkedHeap<T : Any> {
    @Suppress("MemberVisibilityCanBePrivate")
    var root: T? = null
        private set

    @Suppress("MemberVisibilityCanBePrivate")
    var size: Int = 0
        private set

    protected abstract fun _getParent(child: T): T?
    protected abstract fun _setParent(child: T, parent: T?)
    protected abstract fun _getLeft(parent: T): T?
    protected abstract fun _setLeft(parent: T, leftChild: T?)
    protected abstract fun _getRight(parent: T): T?
    protected abstract fun _setRight(parent: T, rightChild: T?)
    protected abstract fun _compare(left: T, right: T): Int
    protected abstract fun _getSubtreeSize(node: T): ULong
    protected abstract fun _setSubtreeSize(node: T, size: ULong)
    protected open fun _incSubtreeSize(node: T) {
        this._setSubtreeSize(node, this._getSubtreeSize(node) + 1u)
    }

    protected open fun _decSubtreeSize(node: T) {
        this._setSubtreeSize(node, this._getSubtreeSize(node) - 1u)
    }

    private inline fun __throwHeapCorrupted(): Nothing = throw RuntimeException("Heap corrupted")
    private inline fun __throwWrongOwner(): Nothing = throw IllegalArgumentException("Node from another struct or this heap corrupted")
    private inline fun __throwUnreachable(): Nothing = throw RuntimeException("Unreachable")

    private inline fun ___swap(
        parent: T, child: T,
        grandparent: T?,
        setGrandparent2Parent: (T) -> Unit,
        getForwardChild: (T) -> T?,
        setForwardChild: (parent: T, child: T?) -> Unit,
        getOppositeChild: (T) -> T?,
        setOppositeChild: (parent: T, child: T?) -> Unit
    ) {
        contract {
            callsInPlace(setGrandparent2Parent, InvocationKind.EXACTLY_ONCE)
            callsInPlace(getForwardChild, InvocationKind.AT_LEAST_ONCE)
            callsInPlace(setForwardChild, InvocationKind.AT_LEAST_ONCE)
            callsInPlace(getOppositeChild, InvocationKind.AT_LEAST_ONCE)
            callsInPlace(setOppositeChild, InvocationKind.AT_LEAST_ONCE)
        }
        this._setParent(child, grandparent)
        setGrandparent2Parent(child)
        setForwardChild(parent, getForwardChild(child))
        this._setParent(parent, getOppositeChild(parent))
        setOppositeChild(parent, getOppositeChild(child))
        setOppositeChild(child, this._getParent(parent))
        getOppositeChild(child)?.also { oC -> this._setParent(oC, child) }
        getOppositeChild(parent)?.also { oC -> this._setParent(oC, parent) }
        getForwardChild(child)?.also { fC -> this._setParent(fC, parent) }
        setForwardChild(child, parent)
        this._setParent(parent, child)
        val parentHeight = this._getSubtreeSize(parent)
        this._setSubtreeSize(parent, this._getSubtreeSize(child))
        this._setSubtreeSize(child, parentHeight)
    }

    private inline fun __swapLeft(
        parent: T, child: T,
        grandparent: T?,
        setGrandparent2Parent: (T) -> Unit
    ) {
        contract {
            callsInPlace(setGrandparent2Parent, InvocationKind.EXACTLY_ONCE)
        }
        this.___swap(
            parent, child,
            grandparent,
            setGrandparent2Parent,
            getForwardChild = { p -> this._getLeft(p) },
            setForwardChild = { p, c -> this._setLeft(p, c) },
            getOppositeChild = { p -> this._getRight(p) },
            setOppositeChild = { p, c -> this._setRight(p, c) }
        )
    }

    private inline fun __swapRight(
        parent: T, child: T,
        grandparent: T?,
        setGrandparent2Parent: (T) -> Unit
    ) {
        contract {
            callsInPlace(setGrandparent2Parent, InvocationKind.EXACTLY_ONCE)
        }
        this.___swap(
            parent, child,
            grandparent,
            setGrandparent2Parent,
            getForwardChild = { p -> this._getRight(p) },
            setForwardChild = { p, c -> this._setRight(p, c) },
            getOppositeChild = { p -> this._getLeft(p) },
            setOppositeChild = { p, c -> this._setLeft(p, c) }
        )
    }

    private inline fun __replace(
        origin: T, replacement: T,
        parent: T?,
        setParent2Origin: (T) -> Unit
    ) {
        contract {
            callsInPlace(setParent2Origin, InvocationKind.EXACTLY_ONCE)
        }
        setParent2Origin(replacement)
        this._setParent(replacement, parent)
        this._getLeft(origin)
            .also { lC -> this._setLeft(replacement, lC) }
            ?.also { lC -> this._setParent(lC, replacement) }
        this._getRight(origin)
            .also { rC -> this._setRight(replacement, rC) }
            ?.also { rC -> this._setParent(rC, replacement) }
        this._setSubtreeSize(replacement, this._getSubtreeSize(origin))
    }

    open fun link(node: T) {
        if (this._getParent(node) != null || this._getLeft(node) != null || this._getRight(node) != null)
            throw IllegalArgumentException("This node already bound or created wrong")
        this.size++
        val root = this.root
        if (root == null) {
            this.root = node
            this._setSubtreeSize(node, 0u)
            return
        }

        var freeNode: T
        var parent: T = root
        if (this._compare(node, root) > 0) {
            this.__replace(root, node, null) { new -> this.root = new }
            freeNode = root
            parent = node
        } else {
            freeNode = node
        }

        while (true) {
            val left = this._getLeft(root)
            val right = this._getRight(root)
            if (left == null) {
                this._setLeft(parent, freeNode)
                this._setParent(freeNode, parent)
                this._incSubtreeSize(parent)
                this._setSubtreeSize(freeNode, 0u)
                return
            } else if (right == null) {
                this._setRight(parent, freeNode)
                this._setParent(freeNode, parent)
                this._incSubtreeSize(parent)
                this._setSubtreeSize(freeNode, 0u)
                return
            }

            if (this._getSubtreeSize(left) < this._getSubtreeSize(right)) {
                this.__replace(left, freeNode, parent) { child -> this._setLeft(parent, child) }
                parent = freeNode
                freeNode = left
                this._incSubtreeSize(parent)
            } else {
                this.__replace(right, freeNode, parent) { child -> this._setRight(parent, child) }
                parent = freeNode
                freeNode = right
                this._incSubtreeSize(parent)
            }
        }
    }

    private fun __clearNodeRefs(node: T) {
        this._setParent(node, null)
        this._setLeft(node, null)
        this._setRight(node, null)
        this._setSubtreeSize(node, 0u)
    }

    private fun __decreaseParentsSubtreeSize(startingNode: T) {
        var pp: T = startingNode
        var p: T? = startingNode
        while (p != null) {
            this._decSubtreeSize(p)
            pp = p
            p = this._getParent(p)
        }
        if (pp !== this.root)
            this.__throwWrongOwner()
    }


    private inline fun __unlinkProcessNode(
        parent: T?,
        node: T,
        setParent2Node: (T?) -> Unit,
        setNewParent: (T) -> Unit
    ): Int {
        contract {
            callsInPlace(setParent2Node, InvocationKind.EXACTLY_ONCE)
            callsInPlace(setNewParent, InvocationKind.AT_MOST_ONCE)
        }
        val left = this._getLeft(node)
        val right = this._getRight(node)
        if (left == null) {
            if (right == null) {
                setParent2Node(null)
                return UNLINK_LEAF_REACHED
            } else {
                setParent2Node(null)
                return UNLINK_LEAF_REACHED
            }
        } else {
            if (right == null) {
                setParent2Node(null)
                return UNLINK_LEAF_REACHED
            }
        }

        if (this._compare(left, right) > 0) {
            this.__swapLeft(node, left, parent, setParent2Node)
            setNewParent(left)
            return UNLINK_LEFT
        } else if (this._compare(left, right) < 0) {
            this.__swapRight(node, right, parent, setParent2Node)
            setNewParent(right)
            return UNLINK_RIGHT
        } else {
            if (this._getSubtreeSize(left).toInt() - this._getSubtreeSize(right).toInt() > 0) {
                this.__swapLeft(node, left, parent, setParent2Node)
                setNewParent(left)
                return UNLINK_LEFT
            } else {
                this.__swapRight(node, right, parent, setParent2Node)
                setNewParent(right)
                return UNLINK_RIGHT
            }
        }
    }

    open fun unlink(node: T) {
        var parent: T = node
        var state: Int
        val firstParent = this._getParent(node)
        if (firstParent == null) {
            if (this.root !== node)
                this.__throwWrongOwner()
            state = this.__unlinkProcessNode(
                null, node,
                setParent2Node = { c -> this.root = c },
                setNewParent = { p -> parent = p }
            )
        } else {
            parent = firstParent
            if (this._getLeft(parent) === node)
                state = UNLINK_LEFT
            else
                state = UNLINK_RIGHT
        }
        while (true) {
            when (state) {
                UNLINK_LEAF_REACHED -> break
                UNLINK_LEFT -> state = this.__unlinkProcessNode(
                    parent, node,
                    setParent2Node = { c -> this._setLeft(parent, c) },
                    setNewParent = { p -> parent = p }
                )

                UNLINK_RIGHT -> state = this.__unlinkProcessNode(
                    parent, node,
                    setParent2Node = { c -> this._setRight(parent, c) },
                    setNewParent = { p -> parent = p }
                )

                else -> this.__throwUnreachable()
            }
        }
        this.__decreaseParentsSubtreeSize(parent)
        this.__clearNodeRefs(node)
        this.size--
    }

    @Suppress("SpellCheckingInspection")
    open fun rebalanceUp(node: T) {
        var state = REBALANCEUP_UNKNOWN
        while (true) {
            val parent = this._getParent(node)
            if (parent == null) {
                if (this.root === node)
                    this.__throwWrongOwner()
                break
            }
            if (this._compare(node, parent) < 0)
                break
            val grandparent = this._getParent(parent)
            if (grandparent == null && parent !== this.root)
                this.__throwWrongOwner()

            if (state == REBALANCEUP_UNKNOWN) {
                when {
                    node === this._getLeft(parent) -> state = REBALANCEUP_LEFT
                    node === this._getRight(parent) -> state = REBALANCEUP_RIGHT
                    else -> this.__throwHeapCorrupted()
                }
            }

            when (state) {
                REBALANCEUP_LEFT -> {
                    if (grandparent == null) {
                        this.__swapLeft(parent, node, null) { p -> this.root = p }
                    } else {
                        when {
                            parent === this._getLeft(grandparent) -> {
                                this.__swapLeft(parent, node, grandparent) { c -> this._setLeft(grandparent, c) }
                                state = REBALANCEUP_LEFT
                            }

                            parent === this._getRight(grandparent) -> {
                                this.__swapLeft(parent, node, grandparent) { c -> this._setRight(grandparent, c) }
                                state = REBALANCEUP_RIGHT
                            }

                            else -> this.__throwHeapCorrupted()
                        }
                    }
                }

                REBALANCEUP_RIGHT -> {
                    if (grandparent == null) {
                        this.__swapRight(parent, node, null) { p -> this.root = p }
                    } else {
                        when {
                            parent === this._getLeft(grandparent) -> {
                                this.__swapRight(parent, node, grandparent) { c -> this._setLeft(grandparent, c) }
                                state = REBALANCEUP_LEFT
                            }

                            parent === this._getRight(grandparent) -> {
                                this.__swapRight(parent, node, grandparent) { c -> this._setRight(grandparent, c) }
                                state = REBALANCEUP_RIGHT
                            }

                            else -> this.__throwHeapCorrupted()
                        }
                    }
                }

                else -> this.__throwUnreachable()
            }
        }
    }

    private inline fun __rebalanceDownProcessNode(node: T, parent: T?, setParent2Node: (T?) -> Unit, returnParent: (T) -> Unit): Int {
        val left = this._getLeft(node)
        val right = this._getRight(node)
        if (left == null) {
            if (right == null) {
                return REBALANCEDOWN_FINISHED
            } else {
                if (this._compare(node, right) < 0) {
                    this.__swapRight(node, right, parent, setParent2Node)
                    returnParent(right)
                    return REBALANCEDOWN_RIGHT
                } else {
                    return REBALANCEDOWN_FINISHED
                }
            }
        } else {
            if (right == null) {
                if (this._compare(node, left) < 0) {
                    this.__swapLeft(node, left, parent, setParent2Node)
                    returnParent(left)
                    return REBALANCEDOWN_LEFT
                } else {
                    return REBALANCEDOWN_FINISHED
                }
            } else {
                if (this._compare(left, right) > 0) {
                    if (this._compare(node, left) > 0)
                        return REBALANCEDOWN_FINISHED
                    this.__swapLeft(node, left, parent, setParent2Node)
                    returnParent(left)
                    return REBALANCEDOWN_LEFT
                } else {
                    if (this._compare(node, right) > 0)
                        return REBALANCEDOWN_FINISHED
                    this.__swapRight(node, right, parent, setParent2Node)
                    returnParent(right)
                    return REBALANCEDOWN_RIGHT
                }
            }
        }
    }

    @Suppress("SpellCheckingInspection")
    open fun rebalanceDown(node: T) {
        val firstParent = this._getParent(node)
        var parent: T
        var state: Int
        if (firstParent == null) {
            if (this.root !== node)
                this.__throwWrongOwner()
            parent = node
            state = this.__rebalanceDownProcessNode(node, null, { c -> this.root = c }, { p -> parent = p })
        } else {
            when {
                node === this._getLeft(firstParent) -> state = REBALANCEDOWN_LEFT
                node === this._getRight(firstParent) -> state = REBALANCEDOWN_RIGHT
                else -> this.__throwHeapCorrupted()
            }
            parent = firstParent
        }
        while (true) {
            when (state) {
                REBALANCEDOWN_FINISHED -> break
                REBALANCEDOWN_LEFT -> state = this.__rebalanceDownProcessNode(node, parent, { c -> this._setLeft(parent, c) }, { p -> parent = p })
                REBALANCEDOWN_RIGHT -> state = this.__rebalanceDownProcessNode(node, parent, { c -> this._setRight(parent, c) }, { p -> parent = p })
                else -> this.__throwUnreachable()
            }
        }
    }

    @Suppress("SpellCheckingInspection")
    fun rebalance(node: T) {
        this.rebalanceUp(node)
        this.rebalanceDown(node)
    }

    open fun unlinkRootOrNull(): T? {
        val root = this.root ?: return null
        this.unlink(root)
        return root
    }

    fun unlinkRoot(): T = this.unlinkRootOrNull() ?: throw NoSuchElementException("Heap is empty")

    companion object {
        private const val UNLINK_LEAF_REACHED = 0
        private const val UNLINK_LEFT = 1
        private const val UNLINK_RIGHT = 2

        private const val REBALANCEUP_UNKNOWN = 0
        private const val REBALANCEUP_LEFT = 1
        private const val REBALANCEUP_RIGHT = 2

        private const val REBALANCEDOWN_FINISHED = 0
        private const val REBALANCEDOWN_LEFT = 1
        private const val REBALANCEDOWN_RIGHT = 2


        val PARENT_DEFAULT_VALUE: Nothing? = null
        val LEFT_DEFAULT_VALUE: Nothing? = null
        val RIGHT_DEFAULT_VALUE: Nothing? = null
        const val SUBTREE_SIZE_DEFAULT_VALUE: ULong = 0uL
    }
}