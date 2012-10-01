package liquidrods;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed template that can be rendered using {@link Template#render(Object, java.io.Writer)}
 */
public class Template {
    private List<LiquidrodsNode> rootNodes;
    private Config config;

    /**
     * Creates a template. You shouldn't be using this most of the time, but rather {@link Liquidrods#parse(java.io.Reader)} or {@link Liquidrods#parse(String)} to create a template.
     *
     * @param rootNodes the top level nodes of the template
     * @param config    the liquidrods instance to be used with this template.
     */
    public Template(List<LiquidrodsNode> rootNodes, Config config) {
        this.rootNodes = rootNodes;
        this.config = config;
        processIncludes();
        processExtends();
    }

    private void processIncludes() {
        List<LiquidrodsNode> mergedNodes = new ArrayList<LiquidrodsNode>(rootNodes.size());
        for (LiquidrodsNode node : rootNodes) {
            if (node instanceof LiquidrodsNode.Block) {
                LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                if ("include".equals(block.getName())) {
                    final Template included = Liquidrods.parse(block.getArg(), config);
                    mergedNodes.addAll(included.getRootNodes());
                } else {
                    mergedNodes.add(block);
                }
            } else {
                mergedNodes.add(node);
            }
        }

        this.rootNodes = mergedNodes;
    }

    private void processExtends() {
        LiquidrodsNode.Block extend = null;
        boolean nonText = false;
        for (LiquidrodsNode node : rootNodes) {
            if (node instanceof LiquidrodsNode.Variable) {
                nonText = true;
                if (extend != null) {
                    throw new RuntimeException("Invalid template: it extends another template yet it defines a top level variable " + node);
                }
            } else if (node instanceof LiquidrodsNode.Block) {
                LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                if ("extends".equals(block.getName())) {
                    if (extend != null) {
                        throw new RuntimeException("Invalid template: multiple extends directives: found " + node + " while " + extend + " was already defined");
                    } else if (nonText) {
                        throw new RuntimeException("Invalid template: it extends another template yet it defines a top level variable or block");
                    } else {
                        extend = block;
                    }
                } else {
                    nonText = true;
                    if (extend != null) {
                        throw new RuntimeException("Invalid template: it extends another template yet it defines a top level block " + node);
                    }
                }
            }
        }

        if (extend != null) {
            Template parent = Liquidrods.parse(extend.getArg(), config);
            final Map<String, List<LiquidrodsNode>> defines = processDefines(extend);
            List<LiquidrodsNode> mergedNodes = new ArrayList<LiquidrodsNode>(parent.rootNodes.size());
            for (LiquidrodsNode node : parent.rootNodes) {
                if (node instanceof LiquidrodsNode.Block) {
                    final LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                    if ("placeholder".equals(block.getName())) {
                        List<LiquidrodsNode> toInsert = defines.get(block.getArg());
                        if (toInsert != null) {
                            mergedNodes.addAll(toInsert);
                        } else {
                            mergedNodes.addAll(block.getChildren());
                        }
                    } else {
                        mergedNodes.add(block);
                    }
                } else {
                    mergedNodes.add(node);
                }
            }
            this.rootNodes = mergedNodes;
        }
    }

    private Map<String, List<LiquidrodsNode>> processDefines(LiquidrodsNode.Block extend) {
        Map<String, List<LiquidrodsNode>> defines = new HashMap<String, List<LiquidrodsNode>>();
        for (LiquidrodsNode node : extend.getChildren()) {
            if (node instanceof LiquidrodsNode.Variable) {
                throw new RuntimeException("Invalid template: the variable " + node + " must appear inside a define block");
            } else if (node instanceof LiquidrodsNode.Block) {
                LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                if ("define".equals(block.getName())) {
                    defines.put(block.getArg(), block.getChildren());
                } else {
                    throw new RuntimeException("Invalid template: the block " + node + " must appear inside a define block");
                }
            }
        }
        return defines;
    }

    public List<LiquidrodsNode> getRootNodes() {
        return rootNodes;
    }

    /**
     * Render this template using the specified model into the specified writer
     *
     * @param model the model object to resolve properties against
     * @param out   where to write the result
     */
    public void render(Object model, Writer out) {
        Context context = new Context(null, model);
        try {
            for (LiquidrodsNode node : rootNodes) {
                config.defaultRenderer().render(node, context, config, out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "Template " + rootNodes;
    }
}
