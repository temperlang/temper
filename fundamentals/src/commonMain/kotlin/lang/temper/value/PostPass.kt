package lang.temper.value

/**
 * A post-processing pass.
 * Macros may do modifications, and schedule a single post pass that
 * walks over the module root to clean up all of them in a way that
 * happens unambiguously after macro expansions.
 *
 * For example, macros can use *ImportMe* to specify names from
 * known libraries that will need to be imported.
 * At the same time they put one or more in the AST, they can schedule
 * a post pass that, after all such names have been written, maybe by
 * multiple macro expansions, does the following once:
 *
 * 1. Collects all import-me names
 * 2. Looks at existing imports to see if any satisfy those names.
 * 3. If not, allocates a minimal set of local aliases
 * 4. Rewrites import-me to use the local aliases.
 * 5. Adds import statements so that the normal import handling gets
 *    done, and modules end up with the right ImportRecords.
 *
 * PostPass is applicable here because steps 1, 2 involve whole-module
 * analysis and step 5 involves top-level changes based on local
 * requirements.
 */
fun interface PostPass {
    fun rewrite(root: BlockTree)
}
