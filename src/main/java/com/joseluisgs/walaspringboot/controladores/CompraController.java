package com.joseluisgs.walaspringboot.controladores;

import com.joseluisgs.walaspringboot.modelos.Compra;
import com.joseluisgs.walaspringboot.modelos.Producto;
import com.joseluisgs.walaspringboot.modelos.Usuario;
import com.joseluisgs.walaspringboot.servicios.CompraServicio;
import com.joseluisgs.walaspringboot.servicios.ProductoServicio;
import com.joseluisgs.walaspringboot.servicios.UsuarioServicio;
import com.joseluisgs.walaspringboot.utilidades.GeneradorPDF;
import com.joseluisgs.walaspringboot.utilidades.Html2PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.util.*;

@Controller
@RequestMapping("/app") // Ruta por defecto base donde vamos a escuchar
public class CompraController {
    // Necesitamos los siguientes servicios
    // Para manejar la compra
    @Autowired
    CompraServicio compraServicio;

    // Para manejar los productos
    @Autowired
    ProductoServicio productoServicio;

    // Para los usuarios
    @Autowired
    UsuarioServicio usuarioServicio;

    // Para las sesiones
    @Autowired
    HttpSession session;

    // Para PDF
    @Autowired
    Html2PdfService documentGeneratorService;



    // Para mapear el usuario identificado con lo que tenemos almacenado
    private Usuario usuario;

    // Los metodos etiquetados como ModelAtribute, pornen en el modelo el resultado de realizar esta operación
    // Luego lo podremos recuperar en la vista
    @ModelAttribute("carrito")
    public List<Producto> productosCarrito() {
        // Obtengo una lista de id alacenados en la sesión como carrito
        List<Long> contenido = (List<Long>) session.getAttribute("carrito");
        // Devulevo la lista de productos que tienen la id almacenada en la sesion
        return (contenido == null) ? null : productoServicio.variosPorId(contenido);
    }

    // Calcula el total del carrito
    @ModelAttribute("total_carrito")
    public Double totalCarrito() {
        List<Producto> productosCarrito = productosCarrito(); // Aqui tenemos un ejemplo de como modelar
        if (productosCarrito != null)
            return productosCarrito.stream()
                    .mapToDouble(p -> p.getPrecio())
                    .sum();
        // Podríamos hacerlo con un foreach y no con una expresión lamda. En el fondo estamos recorriendo el array
        // y sumando los valores
        return 0.0;
    }

    // Devuelve el total de items que tiene el carrito. Podemos hacerlo así o con la sesión
    // Lo mostraré en navbar de las dos maneras
    @ModelAttribute("items_carrito")
    public String itemsCarrito() {
        List<Producto> productosCarrito = productosCarrito(); // Aqui tenemos un ejemplo de como modelar
        if (productosCarrito != null)
            return Integer.toString(productosCarrito.size());
        return "";
    }

    // Muestro las compras asociadas al email que se ha registrado en la sesión
    @ModelAttribute("mis_compras")
    public List<Compra> misCompras() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        usuario = usuarioServicio.buscarPorEmail(email);
        return compraServicio.porPropietario(usuario);
    }

    // Como ya tenemos el modelo carrito y total_carrito definido, solo debemos ir a la platilla
    @GetMapping("/carrito")
    public String verCarrito(Model model) {
        return "/app/compra/carrito";
    }

    // AÑadimos un producto al carrito
    @GetMapping("/carrito/add/{id}")
    public String addCarrito(Model model, @PathVariable Long id) {
        // Sacamos el carrito de la sesión
        List<Long> contenido = (List<Long>) session.getAttribute("carrito");
        // Si es nulo, lo creamos
        if (contenido == null)
            contenido = new ArrayList<>();
        // Si no esta el producto lo añadimos. Esto es porque no podemos cambiar la cantidad
        if (!contenido.contains(id))
            contenido.add(id);
        // Almacenamos en la sesión el carrito y el número de items
        session.setAttribute("carrito", contenido);
        session.setAttribute("items_carrito", contenido.size());
        // Volvemos a la página del carrito
        return "redirect:/app/carrito";
    }

    // Elimina un elemento del carrito
    @GetMapping("/carrito/eliminar/{id}")
    public String borrarDeCarrito(Model model, @PathVariable Long id) {
        // Recuperamos el carrito
        List<Long> contenido = (List<Long>) session.getAttribute("carrito");
        // Si es nulo, lo mandamos a public
        if (contenido == null)
            return "redirect:/public";
        // Borramos el contenido
        contenido.remove(id);
        // Si está vacío, elimamos carrito de la sesión
        if (contenido.isEmpty()) {
            session.removeAttribute("carrito");
            session.removeAttribute("items_carrito");
        }

        else {
            // Si no lo añadimos
            session.setAttribute("carrito", contenido);
            session.setAttribute("items_carrito", contenido.size());
        }
        return "redirect:/app/carrito";

    }

    // Finaliza una compra
    @GetMapping("/carrito/finalizar")
    public String checkout() {
        // Recuperamos el carrito
        List<Long> contenido = (List<Long>) session.getAttribute("carrito");
        if (contenido == null) // Es es nulo a public
            return "redirect:/public";

        // Obtenemos la lista de productos
        List<Producto> productos = productosCarrito();
        // Los insertamos en la compra
        Compra c = compraServicio.insertar(new Compra(), usuario);
        // Sitaxis de java nueva por cada producto p, ejecutamos compra servicio y asociamos p a la compra c
        productos.forEach(p -> compraServicio.addProductoCompra(p, c));
        // Elimanos de la sesión el carrito
        session.removeAttribute("carrito");
        session.removeAttribute("items_carrito");

        // Abrimos la factura
        return "redirect:/app/compra/factura/"+c.getId();

    }

    // Mustra las compras e un listado
    @GetMapping("/miscompras")
    public String verMisCompras(Model model) {
        return "/app/compra/listado";
    }

    //Obtiene la factura con un id
    @GetMapping("/compra/factura/{id}")
    public String factura(Model model, @PathVariable Long id) {
        // Recupero la compra mediante su ID
        Compra c = compraServicio.buscarPorId(id);
        // Obtengo la lista de productos por su id asociados a la compra
        List<Producto> productos = productoServicio.productosDeUnaCompra(c);
        // Los metemos en el modelo
        model.addAttribute("productos", productos);
        model.addAttribute("compra", c);
        // Calculamos el total de la compra, es un for each
        model.addAttribute("total_compra", productos.stream().mapToDouble(p -> p.getPrecio()).sum());
        // Devolvemos la factura
        return "/app/compra/factura";
    }

    // Saco una factura en PDF usando itex
    @RequestMapping(value = "/compra/factura/pdf/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> facturaPDF(@PathVariable Long id) {
        // Recupero la compra mediante su ID
        Compra compra = compraServicio.buscarPorId(id);
        // Obtengo la lista de productos por su id asociados a la compra
        List<Producto> productos = productoServicio.productosDeUnaCompra(compra);
        // Total de la compra
        Double total = productos.stream().mapToDouble(p -> p.getPrecio()).sum();

        ByteArrayInputStream bis = GeneradorPDF.factura2PDF(compra, productos, total);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "inline; filename=factura_"+ compra.getId()+".pdf");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(bis));
    }

    // Saco la factura generada con el servicio, le cambio el PATH
    @RequestMapping(value = "/compra/pdf/factura/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity facturaHTML2PDF(@PathVariable Long id) {
        // Cargamos los datos
        // Recupero la compra mediante su ID
        Compra compra = compraServicio.buscarPorId(id);
        // Obtengo la lista de productos por su id asociados a la compra
        List<Producto> productos = productoServicio.productosDeUnaCompra(compra);
        // Total de la compra
        Double total = productos.stream().mapToDouble(p -> p.getPrecio()).sum();

        Map<String, Object> data = new TreeMap<>();
        String factura = "factura_"+ compra.getId();
        data.put("factura", factura);
        data.put("compra", compra);
        data.put("productos", productos);
        data.put("total", total);
        data.put("subtotal", (total/1.21));
        data.put("iva", (total-(total/1.21)));

        InputStreamResource resource = documentGeneratorService.html2PdfGenerator(data);
        if (resource != null) {
            return ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } else {
            return new ResponseEntity(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }


}
