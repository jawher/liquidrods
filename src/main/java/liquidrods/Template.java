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
        String parentTemplate = null;
        boolean nonText = false;
        Map<String, LiquidrodsNode.Block> blocks = new HashMap<String, LiquidrodsNode.Block>();
        for (LiquidrodsNode node : rootNodes) {
            if (node instanceof LiquidrodsNode.Variable) {
                nonText = true;
                if (parentTemplate != null) {
                    throw new RuntimeException("Invalid template: it extends another template yet it defines a top level variable (should be in a block tag)" + node);
                }
            } else if (node instanceof LiquidrodsNode.Block) {
                LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                if ("extends".equals(block.getName())) {
                    if (parentTemplate != null) {
                        throw new RuntimeException("Invalid template: multiple extends directives: found " + node + " while and extend with" + parentTemplate + " was already defined");
                    } else if (nonText) {
                        throw new RuntimeException("Invalid template: it extends another template yet it defines a top level variable or block");
                    } else {
                        parentTemplate = block.getArg();
                    }
                } else if ("block".equals(block.getName())) {
                    blocks.put(block.getArg(), block);
                } else {
                    nonText = true;
                    if (parentTemplate != null) {
                        throw new RuntimeException("Invalid template: it extends another template yet it defines a top level block " + node);
                    }
                }
            }
        }

        if (parentTemplate != null) {
            Template parent = Liquidrods.parse(parentTemplate, config);
            List<LiquidrodsNode> mergedNodes = new ArrayList<LiquidrodsNode>(parent.rootNodes.size());
            for (LiquidrodsNode node : parent.rootNodes) {
                if (node instanceof LiquidrodsNode.Block) {
                    final LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                    if ("block".equals(block.getName())) {
                        LiquidrodsNode.Block toInsert = blocks.get(block.getArg());
                        if (toInsert != null) {
                            handleSuperCalls(toInsert, block);
                            mergedNodes.add(toInsert);
                        } else {
                            mergedNodes.add(block);
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

    private void handleSuperCalls(LiquidrodsNode.Block child, LiquidrodsNode.Block parent) {
        List<LiquidrodsNode> nodes = new ArrayList<LiquidrodsNode>(child.getChildren().size());
        for (LiquidrodsNode node : child.getChildren()) {
            if (node instanceof LiquidrodsNode.Block) {
                LiquidrodsNode.Block block = (LiquidrodsNode.Block) node;
                if ("super".equals(block.getName())) {
                    nodes.addAll(parent.getChildren());
                } else {
                    handleSuperCalls(block, parent);
                    nodes.add(block);
                }
            } else {
                nodes.add(node);
            }
        }
        child.setChildren(nodes);
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
