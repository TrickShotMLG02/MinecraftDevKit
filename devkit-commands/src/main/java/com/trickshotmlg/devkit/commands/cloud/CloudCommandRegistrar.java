package com.trickshotmlg.devkit.commands.cloud;

import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.ArgumentBinding;
import com.trickshotmlg.devkit.commands.ArgumentType;
import com.trickshotmlg.devkit.commands.CommandContext;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandNode;
import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.LongParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.parser.standard.UUIDParser;
import org.incendo.cloud.permission.PredicatePermission;
import org.incendo.cloud.suggestion.Suggestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Registers platform-independent command definitions with an Incendo Cloud v2 manager.
 * Platform code supplies the native sender adapter and may register parsers for custom arguments.
 *
 * @param <C> Cloud's native sender type
 */
public final class CloudCommandRegistrar<C> {
    private final CommandManager<C> manager;
    private final Function<C, CommandSender> senderAdapter;
    private final Map<Argument<?>, ParserDescriptor<? super C, ?>> customParsers = new IdentityHashMap<>();

    public CloudCommandRegistrar(CommandManager<C> manager, Function<C, CommandSender> senderAdapter) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.senderAdapter = Objects.requireNonNull(senderAdapter, "senderAdapter");
    }

    /** Registers a parser descriptor for a {@link ArgumentType#CUSTOM custom argument}. */
    public <T> CloudCommandRegistrar<C> parser(Argument<T> argument, ParserDescriptor<? super C, T> parser) {
        Objects.requireNonNull(argument, "argument");
        Objects.requireNonNull(parser, "parser");
        if (argument.type() != ArgumentType.CUSTOM) {
            throw new IllegalArgumentException("A custom Cloud parser can only be assigned to a CUSTOM argument: " + argument.name());
        }
        if (customParsers.putIfAbsent(argument, parser) != null) {
            throw new IllegalArgumentException("A Cloud parser is already registered for argument '" + argument.name() + "'");
        }
        return this;
    }

    /** Registers all executable nodes in the definition with the Cloud manager. */
    public void register(CommandDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        for (CommandNode root : definition.roots()) validateParsers(root);
        for (CommandNode root : definition.roots()) {
            registerNode(root, root, new ArrayList<>());
        }
    }

    private void validateParsers(CommandNode node) {
        if (node.executable()) {
            for (ArgumentBinding<?> binding : node.arguments()) parserFor(binding.argument());
        }
        node.children().forEach(this::validateParsers);
    }

    private void registerNode(CommandNode root, CommandNode node, List<CommandNode> literalPath) {
        List<CommandNode> path = new ArrayList<>(literalPath);
        if (literalPath.isEmpty()) path.add(root);
        else path.add(node);

        if (node.executable()) registerExecutable(root, node, path);
        for (CommandNode child : node.children()) registerNode(root, child, path);
    }

    private void registerExecutable(CommandNode root, CommandNode endpoint, List<CommandNode> path) {
        Command.Builder<C> builder = manager.commandBuilder(root.name(), root.aliases().toArray(String[]::new));
        for (int index = 1; index < path.size(); index++) {
            CommandNode literal = path.get(index);
            builder = builder.literal(literal.name(), literal.aliases().toArray(String[]::new));
        }
        List<Argument<?>> endpointArguments = new ArrayList<>();
        for (ArgumentBinding<?> binding : endpoint.arguments()) endpointArguments.add(binding.argument());
        for (ArgumentBinding<?> binding : endpoint.arguments()) builder = addArgument(builder, binding, endpointArguments);
        if (!endpoint.permissionRequirements().isEmpty() || !endpoint.senderRequirements().isEmpty()) {
            builder = builder.permission(PredicatePermission.of(sender ->
                    endpoint.isVisibleTo(Objects.requireNonNull(senderAdapter.apply(sender), "senderAdapter result"))));
        }
        Command.Builder<C> executableBuilder = builder.handler(cloudContext -> {
            CommandSender sender = Objects.requireNonNull(senderAdapter.apply(cloudContext.sender()), "senderAdapter result");
            Map<Argument<?>, Object> values = new HashMap<>();
            for (ArgumentBinding<?> binding : endpoint.arguments()) {
                values.put(binding.argument(), readValue(cloudContext, binding.argument()));
            }
            try {
                endpoint.executor().orElseThrow().execute(new CommandContext(sender, values));
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new RuntimeException("Command handler failed", exception);
            }
        });
        manager.command(executableBuilder);
    }

    private Command.Builder<C> addArgument(Command.Builder<C> builder, ArgumentBinding<?> binding,
                                           List<Argument<?>> endpointArguments) {
        return addArgumentTyped(builder, binding, endpointArguments);
    }

    private <T> Command.Builder<C> addArgumentTyped(Command.Builder<C> builder, ArgumentBinding<T> binding,
                                                    List<Argument<?>> endpointArguments) {
        Argument<T> argument = binding.argument();
        ParserDescriptor<? super C, T> parser = parserFor(argument);
        CommandComponent.Builder<C, T> component = CommandComponent.<C, T>builder(argument.name(), parser);
        argument.suggestions().ifPresent(provider -> component.suggestionProvider((context, input) ->
                provider.suggest(frameworkContext(context, endpointArguments), input.lastRemainingToken())
                        .thenApply(values -> values.stream().map(value -> Suggestion.suggestion(value)).toList())
                        .toCompletableFuture()));
        if (binding.required()) return builder.required(component.required());
        if (binding.defaultValue().isPresent()) {
            return builder.optional(component.optional(DefaultValue.constant(binding.defaultValue().orElseThrow())));
        }
        return builder.optional(component.optional());
    }

    @SuppressWarnings("unchecked")
    private <T> ParserDescriptor<? super C, T> parserFor(Argument<T> argument) {
        ParserDescriptor<? super C, ?> custom = customParsers.get(argument);
        if (custom != null) return (ParserDescriptor<? super C, T>) custom;
        return (ParserDescriptor<? super C, T>) switch (argument.type()) {
            case WORD -> StringParser.stringParser();
            case GREEDY_STRING -> StringParser.greedyStringParser();
            case INTEGER -> IntegerParser.integerParser(
                    argument.minimum().map(Number::intValue).orElse(Integer.MIN_VALUE),
                    argument.maximum().map(Number::intValue).orElse(Integer.MAX_VALUE));
            case LONG -> LongParser.longParser(
                    argument.minimum().map(Number::longValue).orElse(Long.MIN_VALUE),
                    argument.maximum().map(Number::longValue).orElse(Long.MAX_VALUE));
            case DOUBLE -> DoubleParser.doubleParser(
                    argument.minimum().map(Number::doubleValue).orElse(Double.NEGATIVE_INFINITY),
                    argument.maximum().map(Number::doubleValue).orElse(Double.POSITIVE_INFINITY));
            case BOOLEAN -> BooleanParser.booleanParser();
            case UUID -> UUIDParser.uuidParser();
            case ENUM -> enumParser(argument);
            case CUSTOM -> throw new IllegalStateException("No Cloud parser registered for custom argument '" + argument.name() + "'");
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ParserDescriptor<? super C, T> enumParser(Argument<T> argument) {
        Class enumClass = argument.enumType().orElseThrow(() ->
                new IllegalStateException("Enum argument has no enum type: " + argument.name()));
        return EnumParser.enumParser(enumClass);
    }

    private <T> Object readValue(org.incendo.cloud.context.CommandContext<C> context, Argument<T> argument) {
        CloudKey<T> key = CloudKey.of(argument.name(), argument.valueType());
        return context.optional(key).orElse(null);
    }

    private CommandContext frameworkContext(org.incendo.cloud.context.CommandContext<C> cloudContext,
                                            List<Argument<?>> arguments) {
        Map<Argument<?>, Object> values = new HashMap<>();
        for (Argument<?> argument : arguments) values.put(argument, readValue(cloudContext, argument));
        CommandSender sender = Objects.requireNonNull(senderAdapter.apply(cloudContext.sender()), "senderAdapter result");
        return new CommandContext(sender, values);
    }
}
