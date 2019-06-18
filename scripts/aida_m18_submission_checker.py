'''
This script is designed to parse an archive [.zip, .tgz, .tar.gz] and determine if it is properly structured, and will
work with Next Century's validator.

To run:
python3 aida_m18_submission_checker.py <path/to/archive>

Output will be printed to stdout and/or stderr
'''
import sys                  #for sys.args
from sys import exit as exit
import os                   #for extention access (basename)
import tarfile, zipfile     #for archive access
import re                   #for regex


'''
Globals
'''

#variable to store if we use tar or zip method to access the file
#-1 = error. cannot continue
#0 = .tar.gz, or .tgz
#1 = .zip
FILETYPE = -1

#task types to validate for
TASK_TYPES = ["1a", "1b", "2", "3"]

#list of names of items in the archive - to be populated after determining what filetype the archive is
ARCHIVE_NAMES = []


def eprint(*args, **kwargs):
    """simple function to print to stderr

    :param args: data to print
    :param kwargs: keywords to pass in print args
    """
    #*****************
    # REQUIRES PYTHON 3+
    #*****************
    print(*args, file=sys.stderr, **kwargs)


def do_TA1a_check():
    """
    Check the archive
    TASK 1a Directory structure:
    <TA1aperformer>_<run>
        NIST
            <document_id>.ttl                  (1 to X)
		INTER-TA
		    <document_id>.ttl                  (0 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """

    ttls_found_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    for name in ARCHIVE_NAMES:

        #split into levels
        path_items = re.split('/', name)

#TODO CHECK TA1A AND TA1B FOR IF PATH_ITEMS[-1] IS EVER NOT THE LOCATION OF THE .TTL FILE NAME

        if '.ttl' in path_items[-1]:

            ttls_found_count = ttls_found_count + 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'nist', 'valid.ttl']
            #rule: ttl needs to be in directory named nist, or inter-ta
            try:
                if ((path_items[-2].upper() == 'NIST' or path_items[-2].upper() == 'INTER-TA') and len(path_items) == 3):
                    ttls_valid_count = ttls_valid_count + 1
                else:
                    ttls_error_warning_count = ttls_error_warning_count + 1
            except:
                ttls_error_warning_count = ttls_error_warning_count + 1

    print("\nTA_1a Archive Validator report:")
    print("INFO: TTL files found: " + str(ttls_found_count))
    print("INFO: TTL files in valid locations: " + str(ttls_valid_count))
    if ttls_error_warning_count > 0:
        print("ERROR: TTL files found in an invalid location: " + str(ttls_error_warning_count))
        print(".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def do_TA1b_check():
    """
    Task 1b directory structure:
    <TA1performer>_<run>
        NIST
            hypothesisID			(1 to Y)
                <document_id>.ttl 		(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """
    ttls_found_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    for name in ARCHIVE_NAMES:

        #split into levels
        path_items = re.split('/', name)

        if '.ttl' in path_items[-1]:

            ttls_found_count = ttls_found_count + 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 3rd level -- aka 4th item in list['name', 'nist', 'hypothesis', 'valid.ttl']
            #rule: ttl needs to be in a subdirectory within the nist directory
            try:
                if path_items[-3].upper() == 'NIST' or len(path_items) == 4:
                    ttls_valid_count = ttls_valid_count + 1
                else:
                   ttls_error_warning_count = ttls_error_warning_count + 1
            except:
                ttls_error_warning_count = ttls_error_warning_count + 1

    print("\nTA_1b Archive Validator report:")
    print("INFO: TTL files found: " + str(ttls_found_count))
    print("INFO: TTL files in valid locations: " + str(ttls_valid_count))
    if ttls_error_warning_count > 0:
        print("ERROR: TTL files found in an invalid location: " + str(ttls_error_warning_count))
        print("ttl files must be located the appropriate hypothesis_id subdirectory of the <run_ID>/NIST/ directory. i.e. <run_id>/NIST/<hypothesis_id>/file.ttl")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def do_TA2_check():
    """
    Task 2 directory structure:
    <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>
        NIST
            <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>.ttl	(1)
        INTER-TA
            <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>.ttl	(0 or 1)


    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    we do not check for all NIST standards set by the document, only for basic directory structure and a NIST/INTER-TA directory
    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """

    ttls_found_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    for name in ARCHIVE_NAMES:

        #split into levels
        path_items = re.split('/', name)

        if '.ttl' in path_items[-1]:

            ttls_found_count = ttls_found_count + 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'nist', 'valid.ttl']
            #rule: ttl needs to be in directory named nist, or inter-ta
            try:
                if ((path_items[-2].upper() == 'NIST' or path_items[-2].upper() == 'INTER-TA') and len(path_items) == 3):
                    ttls_valid_count = ttls_valid_count + 1
                else:
                    ttls_error_warning_count = ttls_error_warning_count + 1
            except:
                ttls_error_warning_count = ttls_error_warning_count + 1


    print("\nTA_2 Archive Validator report:")
    print("INFO: TTL files found: " + str(ttls_found_count))
    print("INFO: TTL files in valid locations: " + str(ttls_valid_count))
    if ttls_error_warning_count > 0:
        print("ERROR: TTL files found in an invalid location: " + str(ttls_error_warning_count))
        print(".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def do_TA3_check():
    """
    Task 3 directory structure:
    <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>
        <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>.<SIN ID>.<SIN frame ID>.<H followed by three digits>.ttl			(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    we do not check for all NIST standards set by the document, only for basic directory structure.
    :return: True if it appears submit ready, False otherwise
    :rtype: bool
    """

    ttls_found_count = 0
    ttls_error_warning_count = 0

    for name in ARCHIVE_NAMES:
        print(name)
        #split into levels
        path_items = re.split('/', name)
        print(path_items)
        if '.ttl' in path_items[-1]:

            ttls_found_count = ttls_found_count + 1

            #validate that the ttl file is in an acceptable directory
            #rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'nist', 'valid.ttl']
            #rule: ttl needs to be in directory named nist, or inter-ta

            if len(path_items) is not 2:
                ttls_error_warning_count = ttls_error_warning_count + 1

    print("\nTA_3 Archive Validator report:")
    print("INFO: TTL files found: " + str(ttls_found_count))
    print("INFO: TTL files in valid locations: " + str(ttls_found_count - ttls_error_warning_count))
    if ttls_error_warning_count > 0:
        print("ERROR: TTL files found in an invalid location: " + str(ttls_error_warning_count))
        print(".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory")

    if ttls_found_count > 0 and ttls_error_warning_count == 0:
        return True
    return False


def get_task_type(file, args):
    """

    :param file: the archive file
    :param args: the runtime arguments for the program -- used to determine the task type
    :return: int corresponding to the task type (see TASK_TYPE)
    :rtype: int
    """

    #user did not tell us what task type it was, we must determine it ourselves.
    #do this with counting the number of .'s in the filename (get rid of .tar.gz first though)
    basename = os.path.basename(file)

    #get rid of extra count if extention is .tar.gz
    dot_count = basename.count('.') if not '.tar.gz' in basename else basename.count('.') - 1

    if dot_count == 1:
        #if theres 1 dot_count, we need to check to see if it is 1a or 1b.

        '''
        determining if 1a or 1b:
            if .ttl file in "NIST" directory then 1a --> .*\/NIST\/[^\/]+.ttl   mode=/i
            if directory in "NIST" directory and no .ttl in "NIST" directory, then 1b   --> .*\/NIST\/\S*\/\S*\.ttl mode =/i
            
        '''
        #type count and ta1_temp used to determine if 1a or 1b
        type_count = 0
        ta1_ret_val = 0

        names_str = " ".join(ARCHIVE_NAMES)
        ta1a_dir_struct_regex = re.compile(".*\/NIST\/[^\/]+.ttl", re.IGNORECASE)
        ta1b_dir_struct_regex = re.compile(".*\/NIST\/\S*\/\S*\.ttl", re.IGNORECASE)

        #if ttls located in the nist directory, mark as potential TA_1a
        if ta1a_dir_struct_regex.search(names_str) is not None:
            ta1_ret_val = 0
            type_count = type_count + 1
        #if ttls located at some subdirectory of the nist directory, mark as potential TA_1b
        if ta1b_dir_struct_regex.search(names_str) is not None:
            ta1_ret_val = 1
            type_count = type_count + 1
        #if ttls were found in both the nist/ directory and a subdirectory of nist/, mark as TA_1a and have the ta_1a do this check too
        if ta1_ret_val > -1 and type_count > 1 :        #use > and not == for scaleability? idk dont @ me c:
            print ("WARNING: Subdirectories found at the same level of .ttl files in the NIST directory.")
            ta1_ret_val = 0
        return ta1_ret_val
    elif dot_count > 1 and dot_count < 4:
        return dot_count
    else:
        eprint("Unkown archive filetype. Please ensure archive name is properly titled per NIST guidelines.")
        return -1



def validate_archive_filetype(file):
    """
    Checks the input file type. If it is not a valid filetype, the program will
    print an error message and quit the script.
    Valid filetypes: .tgz, .tar.gz, or .zip.

    :param file: path to file including file name and extention
    :return: True if valid filetype and successfully set the global FILETYPE variable, False otherwise
    :rtype: bool
    """
    global FILETYPE
    file_ext = os.path.splitext(file)[-1].lower()
    if file_ext == ".gz" or file_ext == ".tgz":
        FILETYPE = 0
    elif file_ext == ".zip":
        FILETYPE = 1
    else:
        FILETYPE = -1
        return False

    return True

def main():
    """

    :returns: True if script completed successfully, False otherwise
    :rtype: bool
    """
    global FILETYPE
    global TASK_TYPES
    global ARCHIVE_NAMES

    #ensure the archive type is a valid filetype and set the global FILETYPE value
    if not validate_archive_filetype(sys.argv[1]) and FILETYPE >= 0:
        eprint("FATAL ERROR: Archive filetype unknown. Please use .zip, .tgz, or .tar.gz")
        exit(False)

    #get names of items in the archive
    if '-v' in sys.argv: print ("Gathering archive items...")
    archive = zipfile.ZipFile(sys.argv[1], 'r', allowZip64=True) if FILETYPE else tarfile.open(sys.argv[1], mode='r')
    ARCHIVE_NAMES = archive.namelist() if FILETYPE else archive.getnames()

    task_type = get_task_type(sys.argv[1], sys.argv)
    if task_type == -1:
        eprint ("ERROR: Cannot determine the task type! Please input task type to arguments and run again.")
        exit(False)

    print("INFO: archive " + str(sys.argv[1]) + " is detected to be of Task type " + TASK_TYPES[task_type] + ".")

    check_status = False
    if task_type == 0:
        check_status = do_TA1a_check()
    elif task_type == 1:
        check_status = do_TA1b_check()
    elif task_type == 2:
        check_status = do_TA2_check()
    elif task_type == 3:
        check_status = do_TA3_check()


    is_or_not = " is " if check_status else " is not "
    print(sys.argv[1] + is_or_not + "submit ready.")



if __name__ == "__main__": main()
