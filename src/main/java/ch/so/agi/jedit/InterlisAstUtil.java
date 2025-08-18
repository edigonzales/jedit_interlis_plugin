package ch.so.agi.jedit;

import ch.interlis.ili2c.metamodel.*;

import java.util.*;

/**
 * Stateless helpers for navigating ili2c's AST (TransferDescription, Model, Topic, Viewable…).
 * Keep this independent of jEdit UI so it can be reused from SideKick, hyperlinks, etc.
 */
public final class InterlisAstUtil {
    private InterlisAstUtil() {}

    /** Resolve model by (case-insensitive) name: prefer models declared in the current file,
     *  otherwise search their imports transitively. Returns null if not found. */
    public static Model resolveModel(TransferDescription td, String name) {
        if (td == null || name == null) return null;
        String target = name.toUpperCase(Locale.ROOT);

        // Prefer models declared in this file
        for (Model m : td.getModelsFromLastFile()) {
            String n = m.getName();
            if (n != null && n.toUpperCase(Locale.ROOT).equals(target)) return m;
        }
        // Then search imports transitively
        for (Model m : td.getModelsFromLastFile()) {
            Model found = findInImportsRecursive(m, target, new HashSet<>());
            if (found != null) return found;
        }
        return null;
    }

    /** DFS through imported models, avoiding cycles. */
    public static Model findInImportsRecursive(Model m, String targetUpper, Set<Model> seen) {
        if (m == null || !seen.add(m)) return null;
        Model[] imps = m.getImporting();
        if (imps == null) return null;
        for (Model imp : imps) {
            if (imp == null) continue;
            String n = imp.getName();
            if (n != null && n.toUpperCase(Locale.ROOT).equals(targetUpper)) return imp;
            Model found = findInImportsRecursive(imp, targetUpper, seen);
            if (found != null) return found;
        }
        return null;
    }

    /** Find a top-level child (Topic/Viewable/Domain/Unit/Function) by name (case-insensitive). */
    public static Element findChildInModelByName(Model model, String name) {
        if (model == null || name == null) return null;
        String t = name.toUpperCase(Locale.ROOT);
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (!(o instanceof Element)) continue;
            Element e = (Element) o;
            String n = e.getName();
            if (n != null && n.toUpperCase(Locale.ROOT).equals(t)) return e;
        }
        return null;
    }

    /** Find a child by name inside a Container (e.g., Topic), case-insensitive. */
    public static Element findChildInContainerByName(Container container, String name) {
        if (container == null || name == null) return null;
        String t = name.toUpperCase(Locale.ROOT);
        for (Iterator<?> it = container.iterator(); it.hasNext();) {
            Object o = it.next();
            if (!(o instanceof Element)) continue;
            Element e = (Element) o;
            String n = e.getName();
            if (n != null && n.toUpperCase(Locale.ROOT).equals(t)) return e;
        }
        return null;
    }

    /** Does this Viewable have an attribute or role with that name? Case-insensitive. */
    public static boolean hasAttributeOrRole(Viewable v, String name) {
        if (v == null || name == null) return false;
        String t = name.toUpperCase(Locale.ROOT);
        for (Iterator<?> it = v.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement ve = (ViewableTransferElement) it.next();
            Object obj = ve.obj;
            String n = null;
            if (obj instanceof AttributeDef) {
                n = ((AttributeDef) obj).getName();
            } else {
                // RoleDef and others – avoid a hard dependency; fall back to reflection.
                try { n = (String) obj.getClass().getMethod("getName").invoke(obj); }
                catch (Exception ignore) {}
            }
            if (n != null && n.toUpperCase(Locale.ROOT).equals(t)) return true;
        }
        return false;
    }

    /** Immediate children names of a parent node for completion. */
    public static List<String> collectChildrenNames(Object parent) {
        ArrayList<String> out = new ArrayList<>();
        if (parent instanceof Model) {
            Model m = (Model) parent;
            for (Iterator<?> it = m.iterator(); it.hasNext();) {
                Object o = it.next();
                if (!(o instanceof Element)) continue;
                Element e = (Element) o;
                String n = e.getName();
                if (n == null) continue;
                if (e instanceof Topic
                 || e instanceof Viewable
                 || e instanceof Domain
                 || e instanceof Unit
                 || e instanceof Function) {
                    out.add(n);
                }
            }
        } else if (parent instanceof Topic) {
            Topic t = (Topic) parent;
            for (Iterator<?> it = t.iterator(); it.hasNext();) {
                Object o = it.next();
                if (!(o instanceof Element)) continue;
                Element e = (Element) o;
                String n = e.getName();
                if (n == null) continue;
                if (e instanceof Viewable
                 || e instanceof Domain
                 || e instanceof Unit
                 || e instanceof Function) {
                    out.add(n);
                }
            }
        } else if (parent instanceof Viewable) {
            Viewable v = (Viewable) parent;
            for (Iterator<?> it = v.getAttributesAndRoles2(); it.hasNext();) {
                ViewableTransferElement ve = (ViewableTransferElement) it.next();
                Object obj = ve.obj;
                if (obj instanceof AttributeDef) {
                    String n = ((AttributeDef) obj).getName();
                    if (n != null) out.add(n);
                } else {
                    try {
                        String n = (String) obj.getClass().getMethod("getName").invoke(obj);
                        if (n != null) out.add(n);
                    } catch (Exception ignore) {}
                }
            }
        }
        return out;
    }
}