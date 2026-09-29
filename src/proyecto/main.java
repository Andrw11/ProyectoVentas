package proyecto;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Procesa la información de los archivos de texto generados para calcular
 * y generar los reportes de ventas por vendedor y por producto.
 *
 * @author Equipo de desarrollo
 * @version 1.0
 */
public class main {

    private static final String OUTPUT_DIRECTORY = "archivos";
    private static final String PRODUCTS_FILE = "productos.txt";
    private static final String SALESMEN_INFO_FILE = "vendedores.txt";
    
    private static final String SELLERS_REPORT_FILE = "reporte_vendedores.csv";
    private static final String PRODUCTS_REPORT_FILE = "reporte_productos.csv";

    /**
     * Estructura interna para representar a un vendedor y su dinero recaudado.
     */
    private static class SalesmanReport {
        String sellerName;
        double totalRevenue;

        public SalesmanReport(String sellerName, double totalRevenue) {
            this.sellerName = sellerName;
            this.totalRevenue = totalRevenue;
        }
    }

    /**
     * Estructura interna para representar un producto y sus unidades vendidas.
     */
    private static class ProductReport {
        String productName;
        double price;
        int totalQuantitySold;

        public ProductReport(String productName, double price, int totalQuantitySold) {
            this.productName = productName;
            this.price = price;
            this.totalQuantitySold = totalQuantitySold;
        }
    }

    /**
     * Punto de entrada de la aplicación de procesamiento.
     *
     * @param args argumentos de línea de comandos (no se utilizan).
     */
    public static void main(String[] args) {
        try {
            // 1. Cargar precios y nombres de productos desde productos.txt
            Map<Integer, ProductInfo> productMap = loadProducts();

            // 2. Cargar información base de los vendedores desde vendedores.txt
            Map<String, String> salesmanMap = loadSalesmen();

            // 3. Procesar las ventas de cada vendedor y calcular ingresos totales
            Map<String, Double> salesmanRevenueMap = new HashMap<>();
            // Mapeo para el reporte de productos: IDProducto -> Cantidad total vendida
            Map<Integer, Integer> productSalesCountMap = new HashMap<>();

            // Inicializar contadores de productos
            for (Integer prodId : productMap.keySet()) {
                productSalesCountMap.put(prodId, 0);
            }

            // Leer archivos de ventas individuales en la carpeta "archivos"
            Path dirPath = Paths.get(OUTPUT_DIRECTORY);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "vendedor_*.txt")) {
                for (Path salesmanFile : stream) {
                    processSalesFile(salesmanFile, productMap, salesmanRevenueMap, productSalesCountMap);
                }
            }

            // 4. Generar el reporte de vendedores (Punto 3 del proyecto)
            generateSalesmenReport(salesmanMap, salesmanRevenueMap);
            System.out.println("Reporte de vendedores generado correctamente.");

            // 5. Generar el reporte de productos (Punto 4 del proyecto)
            generateProductsReport(productMap, productSalesCountMap);
            System.out.println("Reporte de productos generado correctamente.");

        } catch (IOException | NumberFormatException e) {
            System.out.println("Se produjo un error durante el procesamiento: " + e.getMessage());
        }
    }

    /**
     * Clase auxiliar para almacenar precio y nombre del producto.
     */
    private static class ProductInfo {
        String name;
        double price;

        public ProductInfo(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }

    /**
     * Carga los productos desde el archivo de entrada y los almacena por ID.
     *
     * @return mapa con el ID del producto como clave y su información como valor.
     * @throws IOException si ocurre un error al leer el archivo.
     * @throws NumberFormatException si un dato numérico del archivo no es válido.
     */
    private static Map<Integer, ProductInfo> loadProducts() throws IOException {
        Map<Integer, ProductInfo> products = new HashMap<>();
        Path filePath = Paths.get(OUTPUT_DIRECTORY, PRODUCTS_FILE);

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("IDProducto;")) {
                    continue;
                }

                String[] parts = line.split(";");
                if (parts.length >= 3) {
                    int id = Integer.parseInt(parts[0].trim());
                    String name = parts[1].trim();
                    double price = Double.parseDouble(parts[2].trim());
                    products.put(id, new ProductInfo(name, price));
                }
            }
        }
        return products;
    }

    /**
     * Carga la información de los vendedores desde el archivo de entrada.
     *
     * @return mapa con el número de documento como clave y la información del
     *         vendedor como valor.
     * @throws IOException si ocurre un error al leer el archivo.
     */
    private static Map<String, String> loadSalesmen() throws IOException {
        Map<String, String> salesmen = new HashMap<>();
        Path filePath = Paths.get(OUTPUT_DIRECTORY, SALESMEN_INFO_FILE);

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("TipoDocumento;")) {
                    continue;
                }

                String[] parts = line.split(";");
                if (parts.length >= 4) {
                    String documentNumber = parts[1].trim();
                    salesmen.put(documentNumber, line);
                }
            }
        }
        return salesmen;
    }

    /**
     * Procesa un archivo de ventas de un vendedor y actualiza los acumulados
     * de dinero recaudado y cantidades vendidas por producto.
     *
     * @param file archivo de ventas del vendedor.
     * @param productMap mapa de productos disponibles.
     * @param salesmanRevenueMap mapa de ingresos acumulados por vendedor.
     * @param productSalesCountMap mapa de cantidades vendidas por producto.
     * @throws IOException si ocurre un error al leer el archivo.
     * @throws NumberFormatException si una cantidad o ID no es numérico.
     */
    private static void processSalesFile(Path file, Map<Integer, ProductInfo> productMap,
                                         Map<String, Double> salesmanRevenueMap,
                                         Map<Integer, Integer> productSalesCountMap) throws IOException {
        
        // El nombre del archivo es por ejemplo "vendedor_1001.txt"
        String fileName = file.getFileName().toString();
        String documentNumber = fileName.replace("vendedor_", "").replace(".txt", "").trim();

        double totalRevenueForSalesman = 0.0;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line = reader.readLine(); // La primera línea contiene TipoDoc;Id
            // Las siguientes líneas contienen IdProducto;Cantidad;Fecha (o solo IdProducto;Cantidad)
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    int productId = Integer.parseInt(parts[0].trim());
                    int quantity = Integer.parseInt(parts[1].trim());

                    if (productMap.containsKey(productId)) {
                        double price = productMap.get(productId).price;
                        totalRevenueForSalesman += (price * quantity);

                        // Acumular cantidad total por producto
                        int currentCount = productSalesCountMap.getOrDefault(productId, 0);
                        productSalesCountMap.put(productId, currentCount + quantity);
                    }
                }
            }
        }

        salesmanRevenueMap.put(documentNumber, totalRevenueForSalesman);
    }

    /**
     * Genera el reporte de vendedores ordenado de mayor a menor por dinero
     * recaudado. Cada línea contiene únicamente nombre completo y valor recaudado.
     *
     * @param salesmanMap información base de los vendedores.
     * @param salesmanRevenueMap ingresos acumulados por número de documento.
     * @throws IOException si ocurre un error al escribir el reporte.
     */
    private static void generateSalesmenReport(Map<String, String> salesmanMap,
                                               Map<String, Double> salesmanRevenueMap) throws IOException {
        List<SalesmanReport> reports = new ArrayList<>();

        for (Map.Entry<String, String> entry : salesmanMap.entrySet()) {
            String docNumber = entry.getKey();
            String infoLine = entry.getValue();
            double revenue = salesmanRevenueMap.getOrDefault(docNumber, 0.0);

            String[] parts = infoLine.split(";");
            String sellerName = parts[2].trim() + " " + parts[3].trim();

            reports.add(new SalesmanReport(sellerName, revenue));
        }

        // Ordenar de mayor a menor dinero recaudado (Descendente)
        Collections.sort(reports, new Comparator<SalesmanReport>() {
            @Override
            public int compare(SalesmanReport r1, SalesmanReport r2) {
                return Double.compare(r2.totalRevenue, r1.totalRevenue);
            }
        });

        Path reportPath = Paths.get(OUTPUT_DIRECTORY, SELLERS_REPORT_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(reportPath)) {
            for (SalesmanReport report : reports) {
                writer.write(report.sellerName + ";"
                        + String.format(java.util.Locale.US, "%.2f", report.totalRevenue));
                writer.newLine();
            }
        }
    }

    /**
     * Genera el reporte de productos ordenado de mayor a menor por cantidad
     * total vendida. La cantidad se utiliza para ordenar, pero no se escribe
     * en el archivo final.
     *
     * @param productMap mapa de productos con nombre y precio.
     * @param productSalesCountMap cantidades vendidas acumuladas por producto.
     * @throws IOException si ocurre un error al escribir el reporte.
     */
    private static void generateProductsReport(Map<Integer, ProductInfo> productMap,
                                               Map<Integer, Integer> productSalesCountMap) throws IOException {
        List<ProductReport> reports = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : productSalesCountMap.entrySet()) {
            int productId = entry.getKey();
            int totalQty = entry.getValue();
            ProductInfo info = productMap.get(productId);
            if (info != null) {
                reports.add(new ProductReport(info.name, info.price, totalQty));
            }
        }

        // Ordenar de mayor a menor cantidad vendida (Descendente)
        Collections.sort(reports, new Comparator<ProductReport>() {
            @Override
            public int compare(ProductReport p1, ProductReport p2) {
                return Integer.compare(p2.totalQuantitySold, p1.totalQuantitySold);
            }
        });

        Path reportPath = Paths.get(OUTPUT_DIRECTORY, PRODUCTS_REPORT_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(reportPath)) {
            for (ProductReport report : reports) {
                writer.write(report.productName + ";"
                        + String.format(java.util.Locale.US, "%.2f", report.price));
                writer.newLine();
            }
        }
    }
}