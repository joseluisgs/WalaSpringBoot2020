package com.joseluisgs.walaspringboot.controladores;

import com.joseluisgs.walaspringboot.modelos.Producto;
import com.joseluisgs.walaspringboot.modelos.Usuario;
import com.joseluisgs.walaspringboot.servicios.ProductoServicio;
import com.joseluisgs.walaspringboot.servicios.UsuarioServicio;
import com.joseluisgs.walaspringboot.upload.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import javax.validation.Valid;
import java.nio.file.Files;
import java.util.List;

@Controller
@RequestMapping("/app") // Ruta por defecto
public class ProductoController {

    // Servicio de producto
    @Autowired
    ProductoServicio productoServicio;

    // Servicio de usuario
    @Autowired
    UsuarioServicio usuarioServicio;

    private Usuario usuario;

    // Servicio de almacenamiento
    @Autowired
    StorageService storageService;

    // Inyectamos en el modelo automáticamente la lista de mis productos
    @ModelAttribute("misproductos")
    public List<Producto> misProductos() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        usuario = usuarioServicio.buscarPorEmail(email);
        return productoServicio.productosDeUnPropietario(usuario);
    }

    // Obtenemos la lista de mis productos, de hecho dejamos buscar, recibiendo el modelo
    @GetMapping("/misproductos")
    public String list(Model model, @RequestParam(name = "q", required = false) String query) {
        if (query != null)
            // Asignamos al modelo los productos
            model.addAttribute("misproductos", productoServicio.buscarMisProductos(query, usuario));
        return "app/producto/lista";
    }

    // Eliminamos el producto
    @GetMapping("/misproductos/eliminar/{id}")
    public String eliminar(@PathVariable Long id) {
        // Buscamos el producto
        Producto p = productoServicio.findById(id);
        // Lo borramos si no tiene ninguan compra asociada
        if (p.getCompra() == null)
            // Lo borramos, la imagen se borra en el servicio
            productoServicio.borrar(p);
        return "redirect:/app/misproductos";
    }

    // Creamos un nuevo producto
    @GetMapping("/misproducto/nuevo")
    public String nuevoProductoForm(Model model) {
        // Lo insertamos en el modelo
        model.addAttribute("producto", new Producto());
        return "app/producto/ficha";
    }

    @PostMapping("/misproducto/nuevo/submit")
    public String nuevoProductoSubmit(@ModelAttribute Producto producto, @RequestParam("file") MultipartFile file) {
        // Subimos las imagenes
        if (!file.isEmpty()) {
            String imagen = storageService.store(file);
            producto.setImagen(MvcUriComponentsBuilder
                    .fromMethodName(FilesController.class, "serveFile", imagen).build().toUriString());
        }
        // Indicamos el propietario
        producto.setPropietario(usuario);
        //nsertamos
        productoServicio.insertar(producto);
        return "redirect:/app/misproductos";
    }

    // Actualizamos el producto
    @GetMapping("/misproductos/editar/{id}")
    public String eliminar(@PathVariable Long id, Model model) {
        // Buscamos el producto
        Producto p = productoServicio.findById(id);
        if (p != null) {
            model.addAttribute("producto", p);
            return "app/producto/ficha";
        } else {
            return "redirect:/app/misproductos";
        }
    }

    @PostMapping("/misproductos/editar/submit")
    public String editarEmpleadoSubmit(@ModelAttribute("producto") Producto actualProducto, @RequestParam("file") MultipartFile file) {

        // Lo que tenga que hacer, buscamos el antiguo producto para sacar los datos
        Producto p = productoServicio.findById(actualProducto.getId());
        // Obtenemos el usuario
        // Porque es el único campo que no le hemos podido pasar al formulario
        actualProducto.setPropietario(p.getPropietario());

        // Procesamos las imagenes
        actualProducto.setImagen(p.getImagen());
        // Si existe que me han enviado el fichero y que la imagen antigua está almacenada

        // Asignamos la nueva
        if (!file.isEmpty()) {
            // Borramos la antigua si se puede si existe en el nuestro directorio
                storageService.delete(p.getImagen());
            // Subimos la nueva
            String imagen = storageService.store(file);
            actualProducto.setImagen(MvcUriComponentsBuilder
                    .fromMethodName(FilesController.class, "serveFile", imagen).build().toUriString());
        }
        // Actualizamos el producto
        productoServicio.editar(actualProducto);
        return "redirect:/app/misproductos";
    }


}
