package org.destroyermob.mobsstorage.client;

import java.lang.reflect.Method;
import java.util.Optional;
import net.neoforged.fml.ModList;

/** Optional, reflection-only search integration so EMI and JEI remain soft dependencies. */
final class ItemBrowserSearchBridge {
    private static final Backend BACKEND = discover();

    private ItemBrowserSearchBridge() {
    }

    static boolean available() {
        return BACKEND != null;
    }

    static String name() {
        return BACKEND == null ? "EMI/JEI" : BACKEND.name();
    }

    static Optional<String> searchText() {
        return BACKEND == null ? Optional.empty() : BACKEND.searchText();
    }

    static boolean setSearchText(String query) {
        return BACKEND != null && BACKEND.setSearchText(query == null ? "" : query);
    }

    private static Backend discover() {
        if (ModList.get().isLoaded("emi")) {
            try {
                return new EmiBackend();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (ModList.get().isLoaded("jei")) {
            try {
                return new JeiBackend();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private interface Backend {
        String name();
        Optional<String> searchText();
        boolean setSearchText(String query);
    }

    private static final class EmiBackend implements Backend {
        private final Method getSearchText;
        private final Method setSearchText;

        private EmiBackend() throws ReflectiveOperationException {
            Class<?> api = Class.forName("dev.emi.emi.api.EmiApi");
            getSearchText = api.getMethod("getSearchText");
            setSearchText = api.getMethod("setSearchText", String.class);
        }

        @Override public String name() { return "EMI"; }

        @Override
        public Optional<String> searchText() {
            try {
                return Optional.ofNullable((String) getSearchText.invoke(null));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return Optional.empty();
            }
        }

        @Override
        public boolean setSearchText(String query) {
            try {
                setSearchText.invoke(null, query);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
    }

    private static final class JeiBackend implements Backend {
        private final Method getRuntime;
        private final Method getIngredientFilter;
        private final Method getFilterText;
        private final Method setFilterText;

        private JeiBackend() throws ReflectiveOperationException {
            Class<?> internal = Class.forName("mezz.jei.common.Internal");
            Class<?> runtime = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
            Class<?> filter = Class.forName("mezz.jei.api.runtime.IIngredientFilter");
            getRuntime = internal.getMethod("getJeiRuntime");
            getIngredientFilter = runtime.getMethod("getIngredientFilter");
            getFilterText = filter.getMethod("getFilterText");
            setFilterText = filter.getMethod("setFilterText", String.class);
        }

        @Override public String name() { return "JEI"; }

        private Object filter() throws ReflectiveOperationException {
            Object runtime = getRuntime.invoke(null);
            return runtime == null ? null : getIngredientFilter.invoke(runtime);
        }

        @Override
        public Optional<String> searchText() {
            try {
                Object filter = filter();
                return filter == null ? Optional.empty()
                        : Optional.ofNullable((String) getFilterText.invoke(filter));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return Optional.empty();
            }
        }

        @Override
        public boolean setSearchText(String query) {
            try {
                Object filter = filter();
                if (filter == null) return false;
                setFilterText.invoke(filter, query);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
    }
}
