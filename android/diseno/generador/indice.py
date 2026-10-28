import json,sys
import os
R=os.path.join(os.environ.get("SIGRO_LIENZO", os.path.join(os.path.dirname(__file__), "..")), "project", "canvas.json")
PAGINAS={
 "catalogo":[("CatBuscar",844,"3.1 · Buscar insumo (HU-10, HU-09)"),("CatFicha",1080,"3.2 · Ficha: foto, factor y nombres comerciales (HU-11, HU-17, HU-09)"),("CatNuevo",1120,"3.3 · Registrar insumo nuevo (HU-08, HU-10)")],
 "herramientas":[("HerLista",900,"4.1 · Herramientas de la obra (HU-51, HU-52)"),("HerFicha",1000,"4.2 · Ficha e historial (HU-51)"),("HerIngreso",1000,"4.3 · Ingreso de herramienta (HU-48)"),("HerEntregar",900,"4.4 · Entregar a un trabajador (HU-49)"),("HerDevolver",1000,"4.5 · Devolución con estado (HU-50)"),("HerMantBaja",1000,"4.6 · Mantenimiento o baja (HU-52, HU-53)")],
 "manoobra":[("MOTrabajadores",1000,"5.1 · Trabajadores de la obra (HU-62)"),("MONuevo",1000,"5.2 · Registrar trabajador (HU-62)"),("MOAsistencia",1200,"5.3 · Asistencia del día (HU-63)")],
}
NOTAS={"catalogo":("planCat","Catálogo: los insumos son REALES del catálogo del cliente (código, nombre, unidad, marca). Los nombres comerciales son ejemplos de cómo los llaman en obra. Prueba en 3.1: «cemento sol», «king kong», «1432». En 3.2 intenta agregar «cemento sol»: el sistema avisa que ya es de otro insumo."),
       "herramientas":("planHer","Herramientas: cada una se registra por separado (no por cantidad). Estados: disponible · en uso · en mantenimiento · dada de baja. La baja la registra el almacenero y la autoriza el residente. Herramientas, códigos y personas son datos de ejemplo. El estado al cierre (HU-54) va en «Cerrar obra»."),
       "manoobra":("planMO","Mano de obra: lo registran el almacenero o el ingeniero SSOMA. El DNI se consulta en RENIEC (Integraciones). La asistencia se guarda sin señal como las salidas y se envía después. Personas y DNI: ejemplos, nunca datos reales.")}
def main(paginas):
    c=json.load(open(R))
    for pg in paginas:
        x=0; hmax=0
        for nombre,h,t in PAGINAS[pg]:
            k=nombre+".dc.html"
            c["boards"][k]={"x":x,"y":0,"w":390,"h":h,"title":t,"is_interactive":True,"radius":40,"page":pg}
            if k not in c["order"]: c["order"].append(k)
            x+=470; hmax=max(hmax,h)
        nid,txt=NOTAS[pg]
        c["notes"][nid].update({"x":0,"y":hmax+120,"w":640,"text":txt})
        tit={"catalogo":"tituloCat","herramientas":"tituloHer","manoobra":"tituloMO"}[pg]
        c["notes"][tit]["maxW"]=max(x-80,1800)
    json.dump(c,open(R,"w"),ensure_ascii=False,indent=2)
main(sys.argv[1:])
